package com.kissthinker.xml;

import static com.kissthinker.object.ClassUtil.path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import com.kissthinker.text.StringUtil;
import com.kissthinker.xml.XML.AttributeCallback;

/**
 * Test {@link XML}
 * @author David Ainslie
 *
 */
public class XMLTest
{
    /**
     *
     */
    @Test
    public void create()
    {
        XML xml = createXML();
        System.out.println("\n--- Start ---");
        System.out.println(xml);
        assertTrue(xml.toString().contains("id=\"scooby\""));
    }

    /**
     *
     */
    @Test
    public void toMap()
    {
        XML xml = createXML();
        Map<String, String> map = xml.toMap();

        System.out.println("\n--- Start ---");

        for (String key : map.keySet())
        {
            System.out.printf("Key->%s  Value->%s%n", key, map.get(key));
        }
    }

    /**
     *
     */
    @Test
    public void toMapFromInputStream()
    {
        XML xml = createXML();
        Map<String, String> map = XML.toMap(StringUtil.toInputStream(xml.toString()));

        System.out.println("\n--- Start ---");

        for (String key : map.keySet())
        {
            System.out.printf("Key->%s  Value->%s%n", key, map.get(key));
        }
    }

    /**
     *
     */
    @Test
    public void toMapFromURI()
    {
        File file = new File(path("src/test/resources/", getClass(), "/example.xml"));
        Map<String, String> map = XML.toMap(file.toURI());

        System.out.println("\n--- Start ---");

        for (String key : map.keySet())
        {
            System.out.printf("Key->%s  Value->%s%n", key, map.get(key));
        }
    }

    /**
     *
     */
    @Test
    public void parse()
    {
        long start = System.currentTimeMillis();

        XML xml = XML.parse(Thread.currentThread().getContextClassLoader().getResourceAsStream(path(getClass(), "/order.xml")));

        assertEquals("The Author", xml.get("//author/@name|NAME"));
        assertEquals("The Illustrator", xml.get("//illustrator/@name|NAME"));
        assertEquals("20120101", xml.get("//publication/@publicationDate|date"));

        long stop = System.currentTimeMillis();
        System.out.printf("%nParsing and xpath look up in %s milliseconds%n", stop - start);
    }

    /**
     *
     */
    @Test
    public void parseList()
    {
        long start = System.currentTimeMillis();

        XML xml = XML.parse(Thread.currentThread().getContextClassLoader().getResourceAsStream(path(getClass(), "/order.xml")));

        xml.get("//locations/location/@", new AttributeCallback()
        {
            /**
             *
             * @see com.kissthinker.core.xml.XML.AttributeCallback#got(java.util.Map)
             */
            @Override
            public void got(Map<String, String> attributeNameValuePairs)
            {
                for (Entry<String, String> attributeNameValuePair : attributeNameValuePairs.entrySet())
                {
                    System.out.printf("%nAttribute name and value: %s, %s", attributeNameValuePair.getKey(), attributeNameValuePair.getValue());
                }
            }
        });

        long stop = System.currentTimeMillis();
        System.out.printf("%nList parsing and xpath look up in %s milliseconds%n", stop - start);
    }

    /**
     *
     * @return XML
     */
    private XML createXML()
    {
        return XML.create("demo").text("Root blah")
                        .node("demo1").text("Blah 1").attribute("id", "scooby")
                            .node("address").text("Address 1").nodeEnd()
                            .node("country").text("UK").nodeEnd()
                            .nodeEnd()
                        .node("demo2").text("Blah 2")
                            .node("address").text("Address 2").nodeEnd()
                            .node("country").text("USA")
                            .xmlEnd();
    }
}