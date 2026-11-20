package fr.eink.horlogemeteo

/** Un onglet de l'écran principal. */
interface Page {
    val title: String
    val hasAdd: Boolean get() = false
    val hasEdit: Boolean get() = false
    fun onShow()
    fun onHide()
    fun onAdd() {}
    fun onEdit() {}
}
