package net.corda.option.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap
import net.corda.option.datatypes.Spot
import net.corda.option.datatypes.SpotOf

// Simple flow which takes a reference to an Oracle and a number then returns the corresponding nth prime number.
@InitiatingFlow
class QuerySpot(val oracle: Party, val spotOf: SpotOf) : FlowLogic<Spot>() {
    @Suspendable override fun call() = sendAndReceive<Spot>(oracle, spotOf).unwrap { it } }

