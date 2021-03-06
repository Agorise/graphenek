import org.bitcoinj.core.ECKey
import org.bouncycastle.util.encoders.Hex
import org.spongycastle.math.ec.ECPoint
import java.math.BigInteger

object TestSuite {

    var zkconfig = StealthConfig("secp256k1")

    @JvmStatic
    fun main(args: Array<String>) {

        println("Begin Testing Suite:")
        println("")

        //TestBlindCommitment()
        //TestPointNegation()
        //TestECKeyPairGeneration()
        Demos.PrefixBase58Check_Encoding()
        Test_PrefixBase58Check_Decoding()
        Demos.StealthAddress_ProduceAndDecode()
        Demos.StealthAddress_SendAndReceive()
        Benchmarks.ScanningForTransactions()

    }

    /*  **************************************
     *  TESTS:
     */

    @JvmStatic
    fun TestBlindCommitment() {
        println("Testing BlindCommitment:")

        TestBlindCommit(BigInteger("FFFF", 16),
                arrayOf(BigInteger("100")),
                "Normal randomness and value")
        TestBlindCommit(BigInteger("FFFF", 16),
                arrayOf(BigInteger("0")),
                "Value message is zero")
        TestBlindCommit(BigInteger("FFFF", 16),
                arrayOf<BigInteger>(),
                "Value array is empty")
        TestBlindCommit(zkconfig.curveOrder.add(BigInteger("-1")),
                arrayOf(BigInteger("100")),
                "Randomness is almost curve order")
        TestBlindCommit(BigInteger("7"),
                arrayOf(BigInteger("0")),
                "These two should have same X but opposite Y")
        TestBlindCommit(zkconfig.curveOrder.add(BigInteger("-7")),
                arrayOf(BigInteger("0")),
                "These two should have same X but opposite Y")
        TestBlindCommitSneakyException(BigInteger("1"),
                arrayOf(BigInteger("1")),
                "Sneaky Null commit")
        TestBlindCommitException(BigInteger("0"),
                arrayOf(BigInteger("100")),
                "Randomness is zero")
        TestBlindCommitException(zkconfig.curveOrder,
                arrayOf(BigInteger("100")),
                "Randomness is curve order")
        TestBlindCommitException(BigInteger("FFFF", 16),
                arrayOf(zkconfig.curveOrder),
                "Value message is curve order")
        TestBlindCommitException(BigInteger("FFFF", 16),
                arrayOf(BigInteger("100"), BigInteger("200")),
                "Too many values for generators")
    }

    @JvmStatic
    fun TestPointNegation() {

        /* Next test if the various ways to "negate" a point are indeed equivalent.
           I.e., does -2*G == -(2*G) == (N-2)*G ?
           Answer: It DOES, but might not look like it since ECPoint uses an internal Z-coordinate
           that can flip the meaning of the sign on the stored Y-coordinate.  If we output hex-encoded
           points instead, we see the expected behavior.  Addendum: Or we can normalize() points.
         */

        println("")
        println("Testing understanding of point negation:")

        var N = zkconfig.curveOrder
        var G = zkconfig.G
        println("Curve order N is ${N}")

        for (i in 1..3) {
            //println("")
            println("For i = ${i}:")

            var iBI = i.toBigInteger()
            var niBI = (-i).toBigInteger()
            var NiBI = N.subtract(iBI)

            var iG = G.multiply(iBI)
            var niG = G.multiply(niBI)
            var NiG = G.multiply(NiBI)

            /* Note: Run this test outputing hex encoded points. If you display xCoord and yCoord explicitly, you
               will get weird-seeming results owing to the implied z-coordinate in the internal representation.
             */

            println("    i * G   ${Hex.toHexString(iG.getEncoded(true))}  mult: ${iBI}")
            println("   -i * G   ${Hex.toHexString(niG.getEncoded(true))}  mult: ${niBI}")
            println(" (N-i)* G   ${Hex.toHexString(NiG.getEncoded(true))}  mult: ${NiBI}")
        }

    }

    @JvmStatic
    fun TestECKeyPairGeneration() {
        println("")
        println("Testing Generation of EC Key Pairs:")
        println("***********************************")
        println("")

        println("Test 1: Randomly generated keys:")

        var keyPairOTK = zkconfig.generateKeyPair()
        var (pubKeyAddr) = zkconfig.generateKeyPair()

        println("  Generated Key A:")
        println("  Public: ${keyPairOTK.public.toString()}")
        println("  Private: ${keyPairOTK.private.toString()}")
        println("  Generated Key B:")
        println("  Public: ${pubKeyAddr.toString()}")

    }

    @JvmStatic
    fun Test_PrefixBase58Check_Decoding() {
        println("")
        println("=======================================")
        println("** PrefixBase58Check: Decoding Test: **")
        println("=======================================")

        fun Do_Test(inpStr : String, shouldPfx : String, shouldPayloadHex : String) : Unit {
            try {
                val PB = PrefixBase58Check.fromString(inpStr)
                val prefix = PB.prefix
                val payloadHex = PB.payload.toHexString()
                val testResult = if(prefix.contentEquals(shouldPfx) && payloadHex.contentEquals(shouldPayloadHex))
                                 {"PASS"} else {"FAIL"}
                println("${testResult}: For input: ${inpStr.padEnd(20,' ')} Prefix is: ${prefix.padEnd(4,' ')} Payload is ${payloadHex}") }
            catch (e: Throwable) {
                println("FAIL: Input ${inpStr} resulted in unanticipated exception ${e}")
            }
        }
        fun Do_Test_Except(inpStr : String, reason: String = "") {
            try {
                val PB = PrefixBase58Check.fromString(inpStr)
                val reasonStr = if(reason.isNotBlank()) {" (${reason})"} else {""}
                println("FAIL: Input '${inpStr}' did NOT result in expected exception.${reasonStr}") }
            catch(e: Throwable) {
                val reasonStr = if(reason.isNotBlank()) {" (${reason})"} else {""}
                println("PASS: Input: ${("'"+inpStr+"'").padEnd(22,' ')} produced expected exception.${reasonStr}")
            }
        }

        println("*\n* Decoding Simple Byte Sequences:\n*")

        Do_Test("BTS4zNxKW", "BTS", "")
        Do_Test("GRPH4zNxKW", "GRPH", "")
        Do_Test("BTS11115BhVPG", "BTS", "00000000")
        Do_Test("BTS111115BhVPG", "BTS1", "00000000")  // "Looks like" too many 1's but extra 1 interprets as prefix
        Do_Test("GRPH11115BhVPG", "GRPH", "00000000")
        Do_Test("BTSAjsziuRxrX", "BTS", "01010101")

        Do_Test_Except("PrefixTooLong4zNxKW")
        Do_Test_Except("BTS4zNxKWy", "Added extra char")
        Do_Test_Except("BTS4zNxKX", "Changed char")
        Do_Test_Except("prfx1111115BhVPG", "Too many leading ones")
        Do_Test_Except("prfx111115BhVPG", "Too many leading ones")
        Do_Test_Except("prfx1115BhVPG", "Too few leading ones")
        Do_Test_Except(" BTSAjsziuRxrX", "Whitespace")
        Do_Test_Except("BTS AjsziuRxrX", "Whitespace")
        Do_Test_Except("BTSAjsziuRxrX ", "Whitespace")

        println("*\n* Decoding Public Key Payloads:\n*")
        Do_Test("BTS5WcAwQDaxCVDLYgUcBPHJJx8nxKquQCxRBsezN2DQnJ7Ha2VxU",
                "BTS", "025202641a11502db19165dc6b4c2703f76a2016978cf9e4257db32bc435834f49")
        Do_Test("BTS8UYgAf3C7yj2Sq5mrPtGNf4sKtS7spBsL1C5Pzj2zATTiC1Beh",
                "BTS", "03d86b6558850e986fcd756e0e70b6d3c0dab426b7f2bab34bca99874201fa9f47")

        println("*\n* Concludes Test_PrefixBase58Check_Decoding.\n*\n")

    }

    @JvmStatic
    fun Test_StealthAddress_SharedSecrets() {
        println("")
        println("=======================================")
        println("** StealthAddress: Shared Secrets:   **")
        println("=======================================")

        var SA = StealthAddress()

        println("${SA.verboseDescription()}")
        println("${SA}")

        val OTK = ECKey()
        println("\nOTK: ${OTK}")
        println("Shared X:      ${SA.getSharedXCoord(OTK).toHexString()}")
        println("Shared Secret: ${SA.getSharedSecret(OTK).toHexString()}")
        println("Child PubKey:  ${SA.getTxAuthKey(OTK).pubKey.toHexString()}")

        println("*\n* Concludes Test_StealthAddress_SharedSecrets.\n*\n")

    }

    /*  **************************************
     *  HELPER FUNCTIONS FOLLOW:
     */

    @JvmStatic
    fun TestBlindCommit(blind: BigInteger, values: Array<BigInteger>, label: String) {

        var commit =  BlindCommitment(zkconfig, blind, values)
        var C = commit.getECCommitment()
        C = C.normalize()  // Prevent weirdness from degeneracy of representation

        println("Commitment is (x,y): (${C.xCoord}, ${C.yCoord}) :: ${label}")

    }

    @JvmStatic
    fun TestBlindCommitException(blind: BigInteger, values: Array<BigInteger>, label: String) {

        try {
            var commit = BlindCommitment(zkconfig, blind, values)
            var C = commit.getECCommitment()
            C = C.normalize()  // Prevent weirdness from degeneracy of representation

            println("Commitment is (x,y): (${C.xCoord}, ${C.yCoord}) :: ${label}")
            println("Whoops! An exception should have been thrown!")
        } catch(e: Throwable){
            println("Caught Expected Exception (Tried: ${label}; Got: ${e.message})")
        }
    }

    @JvmStatic
    fun TestBlindCommitSneaky(blind: BigInteger, values: Array<BigInteger>, label: String) {

        var commit =  BlindCommitment(zkconfig, blind, values, arrayOf(zkconfig.G.negate()))
        var C = commit.getECCommitment()
        C = C.normalize()  // Prevent weirdness from degeneracy of representation

        println("Commitment is (x,y): (${C.xCoord}, ${C.yCoord}) :: ${label}")

    }

    @JvmStatic
    fun TestBlindCommitSneakyException(blind: BigInteger, values: Array<BigInteger>, label: String) {

        try {
            var commit = BlindCommitment(zkconfig, blind, values, arrayOf(zkconfig.G.negate()))
            var C = commit.getECCommitment()
            C = C.normalize()  // Prevent weirdness from degeneracy of representation

            println("Commitment is (x,y): (${C.xCoord}, ${C.yCoord}) :: ${label}")
            println("Whoops! An exception should have been thrown!")
        } catch(e: Throwable){
            println("Caught Expected Exception (Tried: ${label}; Got: ${e.message})")
        }
    }

}
