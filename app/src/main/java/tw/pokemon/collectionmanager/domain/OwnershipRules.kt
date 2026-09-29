package tw.pokemon.collectionmanager.domain

object OwnershipRules {
    fun mergeQuantity(current: Int, added: Int): Int {
        require(current >= 0) { "目前數量不可為負數" }
        require(added > 0) { "增加數量必須大於 0" }
        return Math.addExact(current, added)
    }

    fun aggregateQuantities(quantities: Iterable<Int>): Int {
        var total = 0
        quantities.forEach { quantity ->
            require(quantity >= 0) { "數量不可為負數" }
            total = Math.addExact(total, quantity)
        }
        return total
    }
}
