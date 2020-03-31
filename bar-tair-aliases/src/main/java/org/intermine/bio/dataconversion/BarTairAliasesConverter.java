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
 * This is the main program
 * @author Asher
 */
public class BarTairAliasesConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "TAIR Gene Aliases";
    private static final String DATA_SOURCE_NAME = "TAIR";
    // A Map to store Genes
    private Map<String, String> geneItems = new HashMap<>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BarTairAliasesConverter(ItemWriter writer, Model model) {
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
        if (currentFile.getName().startsWith("gene_aliases_")) {
            // Create a Iterator and open the tsv file from TAIR.
            Iterator<?> tsvIter = null;

            try {
                tsvIter = parseTabDelimitedReader(reader);
            } catch (Exception e) {
                System.err.println("Cannot parse file: " + getCurrentFile());
            }

            // Just a variable to skip the header
            boolean firstLine = true;
            String currentGene = "";
            String geneAlias = "";

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
                String symbol = line[1];

                // Now check if Gene Id is in the form of AT1G01010.1.
                // It would start with AT, and is 9 characters long (Avoiding regex for speed).
                if (geneId.startsWith("AT") && geneId.length() == 9) {
                    geneId = geneId.toUpperCase();
                } else {
                    continue;
                }

                if (currentGene.isEmpty()) {
                    // New Gene, So create one
                    currentGene = geneId;
                    geneAlias = symbol;
                } else if (currentGene.equals(geneId)) {
                    // The Gene is already seen. So append gene alias
                    geneAlias = geneAlias.concat(", " + symbol);
                } else {
                    // The Gene has changed. First save the old data, then save the now data
                    createBioEntity(currentGene, geneAlias);

                    // Creating new data
                    currentGene = geneId;
                    geneAlias = symbol;
                }
            }
        } else {
            System.err.println("The file: " + currentFile.getName() + " can not be loaded by this loader!");
        }
    }

    /**
     * Create and store a BioEntity item on the first time called.
     *
     * @param primaryId Gene ID like AT1G01010
     * @param geneAlias Gene Alias
     * @throws ObjectStoreException Data store error
     */
    private void createBioEntity(String primaryId, String geneAlias) throws ObjectStoreException {
        // doing only genes here
        Item bioEntity;

        if (!geneItems.containsKey(primaryId)) {
            // Create a Gene if it does not exist. So one TAIR data per gene
            bioEntity = createItem("Gene");
            bioEntity.setAttribute("primaryIdentifier", primaryId);

            // Now add TAIR data
            if (geneAlias != null && !geneAlias.isEmpty()) {
                bioEntity.setAttribute("tairAliases", geneAlias);
            }

            store(bioEntity);
            geneItems.put(primaryId, bioEntity.getIdentifier());
        }
    }
}
