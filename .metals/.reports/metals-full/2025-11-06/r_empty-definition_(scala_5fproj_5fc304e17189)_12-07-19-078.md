file://<WORKSPACE>/unapply.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:

offset: 200
uri: file://<WORKSPACE>/unapply.scala
text:
```scala
case class PersonX(val n: String, val a: Int)
    object UnapplyExample extends App{
        val p = PersonX("Alice", 30)

        p match {
            case PersonX(n, a) => 
                println(@@s"Name: $n, Age: $a") // Name: Alice, Age: 30
            case _ => 
                println("No match")
        }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: 