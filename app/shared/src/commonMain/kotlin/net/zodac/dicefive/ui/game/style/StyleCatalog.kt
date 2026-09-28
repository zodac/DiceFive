package net.zodac.dicefive.ui.game.style

/** Every available [DiceStyle], in the order they're offered on the Styles screen. */
object DiceStyles {
    val all: List<DiceStyle> = listOf(IvoryDiceStyle, FireDiceStyle, BarrelDiceStyle)
    val default: DiceStyle = IvoryDiceStyle
    fun byId(id: String): DiceStyle = all.firstOrNull { it.id == id } ?: default
}

/** Every available [DiceCupStyle], in the order they're offered on the Styles screen. */
object DiceCupStyles {
    val all: List<DiceCupStyle> = listOf(FacetedDiceCupStyle, FireDiceCupStyle, BarrelDiceCupStyle)
    val default: DiceCupStyle = FacetedDiceCupStyle
    fun byId(id: String): DiceCupStyle = all.firstOrNull { it.id == id } ?: default
}

/** Every available [TableBackground], in the order they're offered on the Styles screen. */
object TableBackgrounds {
    val all: List<TableBackground> = listOf(MidnightFeltBackground, FireTableBackground, BarrelTableBackground)
    val default: TableBackground = MidnightFeltBackground
    fun byId(id: String): TableBackground = all.firstOrNull { it.id == id } ?: default
}

/** Every available [DiceMat], in the order they're offered on the Styles screen. */
object DiceMats {
    val all: List<DiceMat> = listOf(TrayBlueMat, FireDiceMat, BarrelDiceMat)
    val default: DiceMat = TrayBlueMat
    fun byId(id: String): DiceMat = all.firstOrNull { it.id == id } ?: default
}
