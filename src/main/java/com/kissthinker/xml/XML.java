package com.kissthinker.xml;

import static java.text.MessageFormat.format;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.kissthinker.object.Singleton;
import com.kissthinker.text.StringUtil;

/**
 * Simple API to work with XML - Essentially a wrapper around an xml string.
 * <br/>
 * Fast at building XML - Internally a StringBuilder is used and nodes, attributes, text are all strings.<br/>
 * Functionality included to convert XML (whether a String or String from some Stream) to a {@link Map}.<br/>
 * TODO Issue with coverting to a map as keys are overwritten - need a key to be mapped to list
 * XMLTest use of example.xml shows this issue as the results are:
 * Key->parent.child.id  Value->whatever 2
 * Key->parent.child  Value->
 * Key->parent.child.grandchild  Value->Some grandchild text
 * Key->parent  Value->
 * <p/>
 * A fluent API allows for a DSL like way of constructing XML, somewhat akin to the way Groovy provides "markup builders".
 * <p/>
 * @author David Ainslie
 *
 */
@Singleton
public final class XML
{
    /** */
    public static final String KEY_SEPARATOR = System.getProperty("xml.key.separator", ".");

    /** */
    private static final Logger LOGGER = LoggerFactory.getLogger(XML.class);

    /** Pattern to parse an XPath string into a regex */
    private static final Pattern XPATH_PATTERN = Pattern.compile("(\\/+)([^\\/]*)", Pattern.DOTALL);

    /** */
    private static final Pattern NODE_PATTERN = Pattern.compile("<(.*?)>", Pattern.DOTALL);

    /** */
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("(\\w*)\\s*=\\s*[\"\']([^\"]+)[\"\']", Pattern.DOTALL);

    /** */
    private static final String NEW_LINE = "\n";

    /** Size of tab to use when creating XML string (from this {@link XML}).
        Using "spaces" instead of "\t" since "\t" can be different per platform/environment */
    private static final String TAB = "    ";

    /** */
    private static final String QUOTE = "\"";

    /** Current number of tabs to use for current line of XML to be output as a string. */
    private static final ThreadLocal<String> TABS = new ThreadLocal<String>()
    {
        /**
         *
         * @see java.lang.ThreadLocal#initialValue()
         */
        @Override
        protected String initialValue()
        {
            return "";
        }
    };

    /** Convenient ThreadLocal to aid making sure that XML is correctly closed off before any outputting. Currently {@link #toString()} does not use this. */
    private static final ThreadLocal<XML> TO_STRING = new ThreadLocal<>();

    /** Parent XML of this object. */
    private final XML parent;

    /** Name of this (XML) node. */
    private final String name;

    /** Attributes of this (XML) node. */
    private final List<Attribute> attributes = new ArrayList<>();

    /** Child (XML) nodes of this (XML) node. */
    private final List<XML> children = new ArrayList<>();

    /** Text to include within this (XML) node. */
    private String text;

    /**
     * Instantiate XML with given name (to represent root node i.e. <root>)
     * @param name
     * @return XML
     */
    public static XML create(String name)
    {
        return new XML(name, null);
    }

    /**
     * The given xml string is parsed into {@link XML}.<br/>
     * TODO Currently no actual parsing, only reading in to allow for xpath lookup.
     * @param xml
     * @return XML
     */
    public static XML parse(String xml)
    {
        return new XML("xml", null).node("xml", xml);
    }

    /**
     * The given xml input stream is parsed into {@link XML}.<br/>
     * TODO Currently no actual parsing, only reading in to allow for xpath lookup.
     * @param xml
     * @return XML
     */
    public static XML parse(InputStream xmlInputStream)
    {
        XML xml = create("xml");
        
        try (Scanner scanner = new Scanner(xmlInputStream))
        {
            while (scanner.hasNextLine())
            {
                if (xml.text == null)
                {
                    xml.text(scanner.nextLine());
                }
                else
                {
                    xml.text(xml.text + scanner.nextLine());
                }
            }
        }        

        return xml;
    }

    /**
     * Create/Convert xml String from a {@link URI} e.g from a file, into a Map<String, String>
     * @param xmlURI location of xml
     * @return Map<String, String>
     */
    public static Map<String, String> toMap(URI xmlURI)
    {
        try
        {
            LOGGER.debug("Parsing of xml from uri {}", xmlURI);
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();

            ValueHandler valueHandler = new ValueHandler();
            saxParser.parse(xmlURI.toURL().openStream(), valueHandler);

            return valueHandler.map();
        }
        catch (Exception e)
        {
            LOGGER.error(format("Failed to parse xml from uri {0}", xmlURI), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Create/Convert xml String from an {@link InputStream} into a Map<String, String>
     * @param xmlInputStream
     * @return Map<String, String>
     */
    public static Map<String, String> toMap(InputStream xmlInputStream)
    {
        try
        {
            LOGGER.debug("Parsing of xml from input stream {}", xmlInputStream);
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();

            ValueHandler valueHandler = new ValueHandler();
            saxParser.parse(new InputSource(xmlInputStream), valueHandler);

            return valueHandler.map();
        }
        catch (Exception e)
        {
            LOGGER.error(format("Failed to parse xml from input stream {0}", xmlInputStream), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Create/Convert xml String into a Map<String, String>
     * @param xml
     * @return Map<String, String>
     */
    public static Map<String, String> toMap(String xml)
    {
        LOGGER.debug("Parsing of xml string {} (by first converting to an input stream)", xml);
        return toMap(StringUtil.toInputStream(xml));
    }

    /**
     * Create/Convert XML into a Map<String, String>
     * @param xml
     * @return Map<String, String>
     */
    public static Map<String, String> toMap(XML xml)
    {
        LOGGER.debug("Parsing of XML {} (by first converting to an input stream)", xml);
        // xml = xml.xmlEnd();
        return toMap(StringUtil.toInputStream(xml.toString()));
    }

    /**
     * Create a node with the given name.<br/>
     * This new node will be a sub node to the current node, where the current node will be the parent.<br/>
     * E.g create <new node> under <current node>
     * @param name of the XML node
     * @return XML of newly created node
     */
    public XML node(String name)
    {
        XML child = new XML(name, this);
        children.add(child);
        return child;
    }

    /**
     * Create a node with the given name and include the node text.<br/>
     * This new node will be a sub node to the current node, where the current node will be the parent.<br/>
     * This is really a convenience method as instead {@link #text(String)} can be called after calling {@link #node(String)}
     * @param name of the XML node
     * @param text to include within the node
     * @return XML of newly created node
     */
    public XML node(String name, String text)
    {
        XML child = new XML(name, this);
        child.text = text;
        children.add(child);
        return child;
    }

    /**
     * Set text of the current node.
     * @param text
     * @return XML this object for fluent API
     */
    public XML text(String text)
    {
        this.text = text;
        return this;
    }

    /**
     * End the current node i.e close it off so that any new nodes will become a sibling instead of a child.
     * @return XML of next node up
     */
    public XML nodeEnd()
    {
        if (parent == null)
        {
            return this;
        }

        return parent;
    }

    /**
     * End the XML i.e close off the root XML (including the closing off of any unclosed child nodes).
     * @return XML of root
     */
    public XML xmlEnd()
    {
        if (parent == null)
        {
            return this;
        }

        return parent.xmlEnd();
    }

    /**
     * Add an attibute to the current node.<br/>
     * The given name may include "&" to separate multiple attribute names where each will be set on the current node with the given value.
     * @param name of the attribute which can be multiple names delimited by "&"
     * @param value of the attribute - as this is an Object and XML deals with strings, the given value should have an appropriate "toString()"
     * @return XML this object for a fluent API
     */
    public XML attribute(String name, Object value)
    {
        String valueString = value == null ? "" : value.toString();

        if (name.indexOf("&") == -1)
        {
            attributes.add(new Attribute(name, valueString));
        }
        else
        {
            for (String andName : name.split("&"))
            {
                attributes.add(new Attribute(andName.trim(), valueString));
            }
        }

        return this;
    }

    /**
     * Get text() or "attribute" form XML.<br/>
     * Internally the given xpath is converted to a regular expression for fast lookup e.g.
     * <pre>
     * Given xpath //ServiceStatus/StatusNbr/text() internally is converted to ServiceStatus.*<StatusNbr>(.*)<\\/StatusNbr>
     * Given xpath //ServiceStatus/@type internally is converted to ServiceStatus.*type.*=.*["'](.*)["']
     * </pre>
     * TODO text() should not include children upon capture
     * @param xpath
     * @return String which could be "text" or "attribute" that was found, otherwise an empty String
     */
    public String get(String xpath)
    {
        StringBuilder xmlRegex = new StringBuilder();
        String nodeName = null;

        Matcher matcher = XPATH_PATTERN.matcher(xpath);

        while (matcher.find())
        {
            if ("//".equals(matcher.group(1)))
            {
                nodeName = matcher.group(2);
                xmlRegex.append("\\b").append(nodeName).append("\\b");
            }
            else if ("/".equals(matcher.group(1)))
            {
                if ("text()".equals(matcher.group(2)))
                {
                    xmlRegex.append("(.*)").append("<\\/").append(nodeName).append(">");
                }
                else if (matcher.group(2).startsWith("@"))
                {
                    if (matcher.group(2).length() == 1) // i.e. this group also ends in @
                    {
                        // Want to return the entire leaf node and its starting parent node, for extraction of all attributes of leaf node.
                        xmlRegex.append("(.*?)>");
                    }
                    else
                    {
                        xmlRegex
                        .append("(.*?)")
                        .append("(\\b")
                        .append(matcher.group(2).substring(1).replaceAll("\\|", "\\\\b\\|\\\\b"))
                        .append("\\b)").append("\\s*=\\s*[\"\']([^\"]+)[\"\']");
                    }
                }
                else
                {
                    nodeName = matcher.group(2);
                    xmlRegex.append("(.*?)").append("(\\b").append(nodeName).append("\\b)");
                }
            }
        }

        LOGGER.debug("Converted xpath: {} to regular expression: {}", xpath, xmlRegex);

        Pattern pattern = Pattern.compile(xmlRegex.toString(), Pattern.DOTALL);

        String match = "";
        matcher = pattern.matcher(text);

        if (matcher.find())
        {
            if (xmlRegex.toString().endsWith("(.*?)>"))
            {
                match = "<" + matcher.group();
            }
            else
            {
                for (int i = 0; i < matcher.groupCount() + 1; i++)
                {
                    match = matcher.group(i);
                }
            }
        }

        return match;
    }

    /**
     * Get text() or "attribute" form XML.<br/>
     * Internally the given xpath is converted to a regular expression for fast lookup.<br/>
     * This method can be thought of as an asynchronous version of {@link #get(String)}
     * @param xpath
     * @param attributeCallback
     * @return boolean true if given xpath was resolved and false otherwise
     */
    public boolean get(String xpath, AttributeCallback attributeCallback)
    {
        String originalXML = text;

        Map<String, String> attributeNameValuePairs = new HashMap<>();

        String nodeAndParent = get(xpath);

        if ("".equals(nodeAndParent))
        {
            return false;
        }

        while (!"".equals(nodeAndParent))
        {
            Matcher matcher = ATTRIBUTE_PATTERN.matcher(nodeAndParent);

            while (matcher.find())
            {
                attributeNameValuePairs.put(matcher.group(1), matcher.group(2));
            }

            attributeCallback.got(attributeNameValuePairs);

            matcher = NODE_PATTERN.matcher(nodeAndParent);
            String lastMatch = "";

            while (matcher.find())
            {
                lastMatch = matcher.group();
            }

            text = text.replaceAll(lastMatch, "");
            nodeAndParent = get(xpath);
        }

        text = originalXML;

        return true;
    }

    /**
     *
     * @return Map<String, String>
     */
    public Map<String, String> toMap()
    {
        return toMap(this);
    }

    /**
     * Upon calling, if {@link #xmlEnd()} has yet to be called, the returned string will only be for the current node.<br/>
     * The commented out code in this implementation would handle automatic closing, but this reduced flexibilty such as logging of added nodes.
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString()
    {
        /*
        FOR automatic "closing" of XML
        if (parent == null)
        {
            TO_STRING.set(this);
        }
        else
        {
            if (TO_STRING.get() == null)
            {
                return xmlEnd().toString();
            }
            else
            {
                XML root = xmlEnd();

                if (!root.equals(TO_STRING.get()))
                {
                    return root.toString();
                }
            }
        }*/

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(TABS.get()).append("<").append(name);

        for (Attribute attribute : attributes)
        {
            stringBuilder.append(format(" {0}={1}{2}{1}", attribute.name(), QUOTE, attribute.value()));
        }

        if (children.isEmpty() && text == null)
        {
            stringBuilder.append("/>");
        }
        else
        {
            stringBuilder.append(">");
        }

        stringBuilder.append(NEW_LINE);

        TABS.set(TABS.get() + TAB);

        if (text != null && !"".equals(text))
        {
            stringBuilder.append(TABS.get()).append(text).append(NEW_LINE);
        }

        for (XML child : children)
        {
            stringBuilder.append(child.toString());
        }

        if (!children.isEmpty() || text != null)
        {
            TABS.set(TABS.get().replaceFirst(TAB, ""));
            stringBuilder.append(TABS.get()).append(format("</{0}>", name)).append(NEW_LINE);
        }
        else
        {
            TABS.set(TABS.get().replaceFirst(TAB, ""));
        }

        return stringBuilder.toString();
    }

    /**
     * Instantiate XML with a given name and set the new node's parent
     * @param name of new node
     * @param parent of current node, which will be null if the new node is the root
     */
    private XML(String name, XML parent)
    {
        this.name = name;
        this.parent = parent;
    }

    /**
     * Callback for when an XPath expression expects to process a list.
     * <br/>
     * @author David Ainslie
     *
     */
    public interface AttributeCallback
    {
        /**
         *
         * @param attributeNameValuePairs
         */
        void got(Map<String, String> attributeNameValuePairs);
    }

    /**
     *
     * @author David Ainslie
     *
     */
    private static class Attribute
    {
        /** */
        private final String name;

        /** */
        private final String value;

        /**
         *
         * @param name of attribute
         * @param value of attibute, which must be a string as XML deals with just text
         */
        private Attribute(String name, String value)
        {
            super();
            this.name = name;
            this.value = value;
        }

        /**
         * Getter
         * @return String
         */
        private String name()
        {
            return name;
        }

        /**
         * Getter
         * @return String
         */
        private String value()
        {
            return value;
        }
    }

    /**
     * To convert xml into a Map<String, String>
     * <br/>
     * TODO Not yet namespace aware<br/>
     * This is a subclass of {@link ContentHandler} and contains the event listeners related to xml parsing.<br/>
     * Note that the "map" has keys ordered by entry.<br/>
     *
     * @author David Ainslie
     *
     */
    private static class ValueHandler extends DefaultHandler
    {
        /** */
        private final Map<String, String> map = new LinkedHashMap<>();

        /** */
        private String key;

        /** */
        private String value = "";

        /** */
        private boolean nodeRead = false;

        /**
         * Called when an element is being read.<br/>
         * This method will store the key of element in {@link #key}. This key will be used in map.<br/>
         * The elements attributes are handled here.
         * @see org.xml.sax.helpers.DefaultHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
         */
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
        {
            if (nodeRead && !"".equals(value))
            {
                map().put(key, value);
            }

            if (key == null)
            {
                key = qName;
            }
            else
            {
                key = key + KEY_SEPARATOR + qName;
            }

            // Process each attribute.
            for (int i = 0; i < attributes.getLength(); i++)
            {
                // Get names and values for each attribute.
                String name = attributes.getQName(i);
                String value = attributes.getValue(i);
                map().put(key + KEY_SEPARATOR + name, value);

                // TODO The following methods are valid only if the parser is namespace aware

                // The uri of the attribute's namespace
                // String namespaceURI = attributes.getURI(i);

                // This is the name without the prefix
                // String localName = attributes.getLocalName(i);
            }
        }

        /**
         * Called when the value of element is being read.<br/>
         * This method will store the value of element in {@link #value}. Both key and value will be stored in map.
         * @see org.xml.sax.helpers.DefaultHandler#characters(char[], int, int)
         */
        @Override
        public void characters(char[] ch, int start, int length) throws SAXException
        {
            value = "";

            for (int i = start; i < (start + length); i++)
            {
                value = value + Character.toString(ch[i]);
            }

            value = value.trim();
            nodeRead = true;
        }

        /**
         *
         * @see org.xml.sax.helpers.DefaultHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
         */
        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException
        {
            if (nodeRead)
            {
                map().put(key, value);
                nodeRead = false;
            }

            if (key.contains(KEY_SEPARATOR))
            {
                key = key.substring(0, key.lastIndexOf(KEY_SEPARATOR));
            }
        }

        /**
         * For internal use only.
         */
        private ValueHandler()
        {
            super();
        }

        /**
         *
         * @return
         */
        private Map<String, String> map()
        {
            return map;
        }
    }
}