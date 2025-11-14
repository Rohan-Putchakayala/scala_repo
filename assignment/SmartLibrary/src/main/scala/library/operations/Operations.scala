package library.operations

import library.items._
import library.users._

implicit val defaultMember: Member = new Member("Default Member")

implicit def stringToBook(title: String): Book = Book(title)

def borrow(item: ItemType)(implicit member: Member): Unit =
  member.borrowItem(item)

def itemDescription(item: ItemType): Unit = item match {
  case Book(t) => println(s"Book: $t")
  case Magazine(t) => println(s"Magazine: $t")
  case DVD(t) => println(s"DVD: $t")
}
