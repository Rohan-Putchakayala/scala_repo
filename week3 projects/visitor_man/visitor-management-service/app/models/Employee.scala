package models

case class Employee(
                     id: Long,
                     name: String,
                     email: String,
                     department: Option[String],
                     role: String,
                     status: String
                   )
