package library.items

sealed trait ItemType { val title: String }
case class Book(title: String) extends ItemType
case class Magazine(title: String) extends ItemType
case class DVD(title: String) extends ItemType
