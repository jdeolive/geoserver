/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.importer;

import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author Ian Schneider <ischneider@opengeo.org>
 */
public class FeatureDataConverterTest {

    @Test
    public void testXMLUnsafeAttributeRenaming() {
        SimpleFeatureType badatts = buildFeatureTypeWithXMLUnsafeAtts();
        badatts = FeatureDataConverter.DEFAULT.convertType(badatts, null, null, null);

        assertEquals("_123_number_first", badatts.getAttributeDescriptors().get(0).getLocalName());
        assertEquals("i_has_spaces", badatts.getAttributeDescriptors().get(1).getLocalName());
    }
    
    @Test
    public void testPostgisConversion() {
        SimpleFeatureType t = FeatureDataConverter.TO_POSTGIS.convertType(buildFeatureTypeWithXMLUnsafeAtts(), null, null, null);
        assertEquals("_123_number_first", t.getAttributeDescriptors().get(0).getLocalName());
        assertEquals("i_has_spaces", t.getAttributeDescriptors().get(1).getLocalName());
    }

    SimpleFeatureType buildFeatureTypeWithXMLUnsafeAtts() {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName("badatts");

        AttributeTypeBuilder attBuilder = new AttributeTypeBuilder();
        attBuilder.setBinding(String.class);
        typeBuilder.add(attBuilder.buildDescriptor("123_number_first"));
        attBuilder.setBinding(String.class);
        typeBuilder.add(attBuilder.buildDescriptor("i has spaces"));

        return typeBuilder.buildFeatureType();
    }
}
