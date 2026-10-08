package app.snag.core

import org.junit.Assert.*
import org.junit.Test

class BetaUpdatePolicyTest {
    @Test fun onlyNewerMatchingEditionIsOffered() {
        val paper = "${BetaUpdatePolicy.REPO}/releases/download/v0.1.5/Snag-Paper.apk"
        assertTrue(BetaUpdatePolicy.accepted(5,6,"paper",paper))
        assertFalse(BetaUpdatePolicy.accepted(5,5,"paper",paper))
        assertFalse(BetaUpdatePolicy.accepted(5,4,"paper",paper))
        assertFalse(BetaUpdatePolicy.accepted(5,6,"classic",paper))
    }
    @Test fun untrustedOrMalformedDownloadLocationsAreRejected() {
        assertFalse(BetaUpdatePolicy.accepted(5,6,"paper","http://github.com/selemenev9-ui/Snag/releases/download/v1/Snag-Paper.apk"))
        assertFalse(BetaUpdatePolicy.accepted(5,6,"paper","${BetaUpdatePolicy.REPO}.evil.test/releases/download/v1/Snag-Paper.apk"))
        assertFalse(BetaUpdatePolicy.accepted(5,6,"paper","${BetaUpdatePolicy.REPO}/releases/download/../Snag-Paper.apk"))
    }
}
