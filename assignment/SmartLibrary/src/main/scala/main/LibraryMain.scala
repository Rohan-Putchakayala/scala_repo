package main

import library.items._
import library.users._
import library.operations._

object LibraryMain extends App {
  val alice = new Member("Alice")

  val book1: Book = Book("Scala Programming")
  borrow(book1)(alice)

  val dvd1: DVD = DVD("Inception")
  borrow(dvd1)

  borrow("Harry Potter")

  val items: List[ItemType] = List(
    Book("FP in Scala"),
    Magazine("Science Today"),
    DVD("Matrix")
  )

  items.foreach(itemDescription)
}
