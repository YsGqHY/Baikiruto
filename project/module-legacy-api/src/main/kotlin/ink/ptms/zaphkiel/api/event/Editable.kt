package ink.ptms.zaphkiel.api.event

interface Editable {

    fun addName(key: String, value: Any)

    fun addLore(key: String, value: Any)

    fun addLore(key: String, value: List<Any>)
}
