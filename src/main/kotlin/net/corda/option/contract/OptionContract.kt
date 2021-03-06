package net.corda.option.contract

import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.newSecureRandom
import net.corda.core.identity.Party
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.TransactionBuilder
import net.corda.option.contact.IOptionContract
import net.corda.option.datatypes.Spot
import net.corda.option.datatypes.SpotOf
import net.corda.option.state.IOUState
import net.corda.option.state.OptionState
import net.corda.option.types.OptionType
import java.time.Instant
import java.util.*

open class OptionContract : IOptionContract {
    /**
     * The verify() function of the contract of each of the transaction's input and output states must not throw an
     * exception for a transaction to be considered valid.
     */
    override fun verify(tx: TransactionForContract) {
        //We should only ever receive one command at a time, else throw an exception
        val command = tx.commands.requireSingleCommand<Commands>()
        val timeWindow: TimeWindow? = tx.timeWindow

        val value = command.value
        when (value) {
            is Commands.Issue -> {
                val output = tx.outputs.single() as OptionState
                val time = timeWindow?.untilTime ?: throw IllegalArgumentException("Issued options must be timestamped")
                requireThat {
                    "the issue transaction is signed by the issuer" using (output.issuer.owningKey in command.signers)
                    "the issue transaction is signed by the owner" using (output.owner.owningKey in command.signers)
                    "Zero input should be consumed when issuing an option." using (tx.inputs.isEmpty())

                    "The strike must be non-negative" using (output.strike.quantity > 0)
                    "the expiry date is not in the past" using (time < output.expiry)

                    "The state is propagated" using (tx.outputs.size == 1)
                }
            }

            is Commands.Trade -> {
                val input = tx.inputs.single() as OptionState
                val output = tx.outputs.single() as OptionState

                requireThat {
                    "The trade is signed by the issuer of the option)" using (input.issuer.owningKey in command.signers)
                    "The trade is signed by the current owner of the option" using (input.owner.owningKey in command.signers)
                    "The trade is signed by the new owner of the option" using (output.owner.owningKey in command.signers)

                    "The option cannot be traded if already exercised" using (!input.exercised)

                    "Only the owner property may change." using (input.strike == output.strike && input.issuer == output.issuer && input.currency == output.currency
                            && input.expiry == output.expiry && input.underlying == output.underlying)
                    "The owner property must change in a trade." using (input.owner != output.owner)

                    "The state is propagated" using (tx.outputs.size == 1)
                }
            }

            is Commands.Exercise -> {
                val input = tx.inputs.single() as OptionState
                // We expect an IOU state as an output on exercising matching the moneyness
                val optionOutput = tx.outputs.filterIsInstance<OptionState>().single()
                val iouOutput = tx.outputs.filterIsInstance<IOUState>().single()
                val time = timeWindow?.fromTime ?: throw IllegalArgumentException("Exercising of the option must be timestamped")

                requireThat {
                    //Exercising the option is constrained by the expiry
                    "Exercising the option is executed before maturity" using (time <= input.expiry)
                    "The option is not already exercised" using (!input.exercised)

                    "The IOU amount equals the strike minus the spot for a Put option" using (iouOutput.amount == calculateMoneyness(input.strike, value.spot.value, input.optionType))

                    "Exercising the option is signed by the current owner of the option" using (input.owner.owningKey in command.signers)

                    "The option must be set to exercised" using (optionOutput.exercised)
                }
            }

            else -> throw IllegalArgumentException("Unrecognised command")
        }

        //general constraints on the shape of the transaction.
        requireThat {
            //Constraints on the signers.
            //"All of the participants must be signers." using (command.signers.toSet() == output.participants.map { it.owningKey }.toSet())
        }
    }

    interface Commands : CommandData {
        class Issue(override val nonce: Long = Math.abs(newSecureRandom().nextLong())): IssueCommand, Commands
        class Trade : TypeOnlyCommandData(), Commands
        class Exercise(val spot: Spot) : Commands
    }

    override fun calculateMoneyness(strike: Amount<Currency>, spot: Amount<Currency>, optionType: OptionType): Amount<Currency> {
        val zeroAmount = Amount.zero(Currency.getInstance("USD"))
        if(optionType.name.equals("CALL"))
        {
            return maxOf(spot - strike, zeroAmount)
        }
        return maxOf(strike - spot, zeroAmount)
    }

    override fun calculatePremium(optionState: OptionState, volatility: Double, riskFreeInterest: Double): Amount<Currency> {
        return 7.DOLLARS
    }

    fun generateIssue(strike: Amount<Currency>,
                      expiryDate: Instant, underlying: String,
                      currency: Currency,
                      issuer: Party, recipient: Party,
                      notary: Party,
                      optionType: OptionType): TransactionBuilder {
        val state = OptionState(strike, expiryDate, underlying, currency, issuer, recipient, optionType)
        return TransactionBuilder(notary = notary).withItems(state, Command(Commands.Issue(), issuer.owningKey))
    }

    fun generateTrade(tx: TransactionBuilder, option: StateAndRef<OptionState>, newOwner: Party) {
        tx.addInputState(option)
        tx.addOutputState(option.state.data.withNewOwner(newOwner).second)
        tx.addCommand(Command(Commands.Trade(), option.state.data.owner.owningKey))
    }

    @Throws(InsufficientBalanceException::class)
    fun generateExercise(tx: TransactionBuilder, option: StateAndRef<OptionState>, vault: VaultService) {
        // Add the cash movement using the states in our vault.
        val spot = Spot(SpotOf(option.state.data.underlying, Instant.now()), option.state.data.spot)
        vault.generateSpend(tx, calculateMoneyness(option.state.data.strike, 1.DOLLARS, option.state.data.optionType), option.state.data.owner)
        tx.addInputState(option)
        tx.addCommand(Command(Commands.Exercise(spot), option.state.data.owner.owningKey))
    }

    /** A reference to the underlying legal contract option and associated parameters. */
    override val legalContractReference: SecureHash = SecureHash.sha256("Prose contract.")
}
