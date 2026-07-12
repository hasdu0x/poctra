package com.poctra.wallet.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.poctra.wallet.R

enum class Glyph(@DrawableRes val resId: Int) {
    Send(R.drawable.ic_send),
    Receive(R.drawable.ic_receive),
    Bridge(R.drawable.ic_arrow_left_right),
    Assets(R.drawable.ic_assets),
    Explore(R.drawable.ic_explore),
    Trade(R.drawable.ic_trade),
    Privacy(R.drawable.ic_privacy),
    Swap(R.drawable.ic_swap),
    SwapVertical(R.drawable.ic_swap_vertical),
    Settings(R.drawable.ic_settings),
    Plus(R.drawable.ic_plus),
    Back(R.drawable.ic_back),
    Fingerprint(R.drawable.ic_fingerprint),
    Pin(R.drawable.ic_pin),
    Refresh(R.drawable.ic_refresh),
    Revoke(R.drawable.ic_revoke),
    Check(R.drawable.ic_check),
    Close(R.drawable.ic_close),
    Wallet(R.drawable.ic_wallet),
    Copy(R.drawable.ic_copy),
    Download(R.drawable.ic_download),
    ChevronDown(R.drawable.ic_chevron_down),
    ChevronUp(R.drawable.ic_chevron_up),
    Overview(R.drawable.ic_overview),
    Browser(R.drawable.ic_browser),
    External(R.drawable.ic_external),
    Go(R.drawable.ic_go),
    AddressBook(R.drawable.ic_address_book),
    Wifi(R.drawable.ic_wifi),
    Moon(R.drawable.ic_moon),
    Sun(R.drawable.ic_sun),
    Drag(R.drawable.ic_drag),
    Info(R.drawable.ic_info),
    History(R.drawable.ic_history),
    Import(R.drawable.ic_import),
    BookKey(R.drawable.ic_book_key),
    FileText(R.drawable.ic_file_earmark_text),
    Key(R.drawable.ic_key),
    Pencil(R.drawable.ic_pencil),
    Trash(R.drawable.ic_trash),
    Evm(R.drawable.ic_evm),
    Eye(R.drawable.ic_eye),
    EyeOff(R.drawable.ic_eye_off),
    OctraIdentity(R.drawable.ic_octra_identity),
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    color: Color = TextTertiary,
) {
    Image(
        painter = painterResource(glyph.resId),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(color),
    )
}
