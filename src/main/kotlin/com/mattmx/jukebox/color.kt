package com.mattmx.jukebox

fun String.branding() = this.replace("<title>", "&#FB4BAE")
    .replace("<light>", "&#FFE1F2")
    .replace("<dull>", "&#bbb1c4")
    .replace("<zap>", "⚡")
    .replace("<arrow>", "⤷")
    .replace("<green>", "&#00E676")
    .replace("<error>", "&#FF331C")