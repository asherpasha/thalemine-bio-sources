package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.intermine.util.FormattedTextParser.parseTabDelimitedReader;

/**
 * This is the main program.
 * @author Asher
 */
public class BarTairFunctionalDescriptionsConverter extends BioFileConverter
{
    private static final String DATASET_TITLE = "TAIR Functional Descriptions";
    private static final String DATA_SOURCE_NAME = "TAIR";
    // A Map to store Genes
    private Map<String, String> geneItems = new HashMap<>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BarTairFunctionalDescriptionsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * Process the file from TAIR.
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File currentFile = getCurrentFile();

        // Only load Functional Descriptions file at this point.
        if (currentFile.getName().startsWith("Araport11_functional_descriptions_")) {
            // Create a Iterator and open the tsv file from TAIR.
            Iterator<?> tsvIter = null;

            try {
                tsvIter = parseTabDelimitedReader(reader);
            } catch (Exception e) {
                System.err.println("Cannot parse file: " + getCurrentFile());
            }

            // Just a variable to skip the header
            boolean firstLine = true;

            assert tsvIter != null;
            while (tsvIter.hasNext()) {

                // Skip header
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                // Now start reading the lines.
                String[] line = (String[]) tsvIter.next();

                // Get data: short_description	Curator_summary	Computational_description
                String geneId = line[0];
                String shortDescription = line[2];
                String curatorSummary = line[3];
                String computationalDescription = line[4];

                // Now check if Gene Id is in the form of AT1G01010.1.
                // It would start with AT, end with .1 and is 11 characters long (Avoiding regex for speed).
                if (geneId.startsWith("AT") && geneId.endsWith(".1") && geneId.length() == 11) {
                    geneId = geneId.substring(0, geneId.length() - 2).toUpperCase();
                } else {
                    continue;
                }

                // Now create TAIR annotation Object
                Item tair = createTAIRDescription(shortDescription, curatorSummary, computationalDescription);
                // Create Gene if it doesn't exists
                createBioEntity(geneId);
                // Make the db relation
                tair.setReference("gene", geneItems.get(geneId));
                store(tair);
            }
        } else {
            System.err.println("The file: " + currentFile.getName() + " can not be loaded by this loader!");
        }
    }

    /**
     * Create the data object.
     *
     * @param shortDescription TAIR short description
     * @param curatorSummary TAIR curator summary
     * @param computationalDescription TAIR computational Description.
     * @return data object
     */
    private Item createTAIRDescription(String shortDescription, String curatorSummary, String computationalDescription) {
        Item tair = createItem("TAIR");
        tair.setAttribute("shortDescription", shortDescription);
        tair.setAttribute("curatorSummary", curatorSummary);
        tair.setAttribute("computationalDescription", computationalDescription);

        return tair;
    }

    /**
     * Create and store a BioEntity item on the first time called.
     *
     * @param primaryId the primaryIdentifier
     * @throws ObjectStoreException Object Store error
     */
    private void createBioEntity(String primaryId) throws ObjectStoreException {
        // doing only genes here
        Item bioentity;

        if (!geneItems.containsKey(primaryId)) {
            bioentity = createItem("Gene");
            bioentity.setAttribute("primaryIdentifier", primaryId);
            store(bioentity);
            geneItems.put(primaryId, bioentity.getIdentifier());
        }
    }
}
