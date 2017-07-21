package net.corda.option.datatypes

import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable
import java.time.Instant
import java.util.*

@CordaSerializable
data class SpotOf(val name: String, val forTime: Instant)

/** A [Spot] represents a price for a named stock */
@CordaSerializable
data class Spot(val of: SpotOf, val value: Amount<Currency>)
{
    /*override fun equals(other: Any?): Boolean {
        if(other != null && other is Spot)
        {

        }
        return false
    }*/
}