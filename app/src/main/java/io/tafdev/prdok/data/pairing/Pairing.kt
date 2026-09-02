package io.tafdev.prdok.data.pairing

/**
 * The persisted result of a successful pairing. Every value is server-resolved
 * (`ulozsi.zamid`, `ulozsi.zamids`, ...), never what the user typed.
 *
 * Presence of a Pairing is what the iOS app calls `setupCompleted`.
 */
data class Pairing(
    /** Device key; auth for every hello.php call. */
    val klic: String,
    /** Employee id; auth (with [ids]) for the HTML portal pages. */
    val id: String,
    /** Employee secret token. */
    val ids: String,
    /** Facility identifier. */
    val provoz: String,
    /** Prebuilt query string for the Ebony gateway page, or null if the server sent none. */
    val skladnik: String?,
)
