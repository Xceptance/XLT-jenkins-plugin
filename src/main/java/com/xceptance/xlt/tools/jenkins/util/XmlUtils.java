/*
 * Copyright (c) 2014-2020 Xceptance Software Technologies GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.xceptance.xlt.tools.jenkins.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.xceptance.xlt.tools.jenkins.logging.LOGGER;

import hudson.FilePath;

public final class XmlUtils
{
    private XmlUtils()
    {
        // Empty
    }

    public static Document parse(final FilePath file)
    {
        return parse(file.getRemote());
    }

    private static DocumentBuilder docBuilder() throws ParserConfigurationException
    {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder();
    }

    public static Document parse(final String uri)
    {
        try
        {
            return docBuilder().parse(uri);
        }
        catch (SAXException | IOException | ParserConfigurationException e)
        {
            LOGGER.error("Failed to parse file from uri: " + uri, e);
            return null;
        }
    }

    public static Node evaluateXPath(final Document document, final String xpath)
    {
        return evaluateXPath(document, xpath, Node.class);
    }
    
    public static String evaluateXPath(final Node node, final String xpath)
    {
        return evaluateXPath(node, xpath, String.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T evaluateXPath(final Node node, final String xpath, final Class<T> clazz)
    {
        QName returnType = XPathConstants.NODE;
        if (clazz != null)
        {
            if (Number.class.isAssignableFrom(clazz))
            {
                returnType = XPathConstants.NUMBER;
            }
            else if (Boolean.class.isAssignableFrom(clazz))
            {
                returnType = XPathConstants.BOOLEAN;
            }
            else if (String.class.isAssignableFrom(clazz))
            {
                returnType = XPathConstants.STRING;
            }
            else if (List.class.isAssignableFrom(clazz))
            {
                returnType = XPathConstants.NODESET;
            }
        }
        return (T) evaluateXPath(node, xpath, returnType);
    }

    public static XPath xpath()
    {
        return XPathFactory.newInstance().newXPath();
    }

    public static Object evaluateXPath(final Node node, final String xpath, final QName returnType)
    {
        try
        {
            final Object result = xpath().evaluate(xpath, node, returnType);
            if (returnType.equals(XPathConstants.NODESET))
            {
                final ArrayList<Node> list = new ArrayList<>();
                if (result != null)
                {
                    final NodeList matches = (NodeList) result;
                    for (int i = 0; i < matches.getLength(); i++)
                    {
                        list.add(matches.item(i));
                    }
                }
                return list;
            }
            return result;
        }
        catch (XPathExpressionException e)
        {
            LOGGER.error("Failed to evaluate XPath: " + xpath, e);
            return null;
        }
    }

}
