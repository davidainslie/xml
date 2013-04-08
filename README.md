Ease the use of XML.

For simple building of XML (within Java) use this DSL e.g.

```java
XML xml =
    XML.create("demo").text("Root blah")
        .node("demo1").text("Blah 1").attribute("id", "scooby")
            .node("address").text("Address 1").nodeEnd()
            .node("country").text("UK").nodeEnd()
            .nodeEnd()
        .node("demo2").text("Blah 2")
            .node("address").text("Address 2").nodeEnd()
            .node("country").text("USA")
            .xmlEnd();
```

The underlying implementation is very lightweight.

Other functionality:
- Convert XML (whether a String or String from some Stream) to a Map.
- Apply an XPath to XML generated via the DSL.
  Currently this is oversimplified where XPath is converted to a regex and with so many scenarios, does not have enough tests.

As an example of XPath, we could create XML (in a test) and lookup (get) Strings from our XML with given XPaths:


```java
XML xml = createXML();

assertEquals("The Author", xml.get("//author/@name|NAME"));
assertEquals("The Illustrator", xml.get("//illustrator/@name|NAME"));
assertEquals("20120101", xml.get("//publication/@publicationDate|date"));
```
  
A work in progress project that has been on the back burner for quite some time.  

Note, this is a Java 7, Maven 3 project.