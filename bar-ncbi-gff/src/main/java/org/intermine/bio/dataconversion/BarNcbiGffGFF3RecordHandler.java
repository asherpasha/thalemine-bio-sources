package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2016 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import org.intermine.bio.io.gff3.GFF3Record;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;

/**
 * A converter/retriever for the BarNcbiGff dataset via GFF files.
 * This is modified from intermine/intermine. Original author is Julie.
 *
 * @author Julie
 * @author Asher
 */

public class BarNcbiGffGFF3RecordHandler extends GFF3RecordHandler
{
    // Asher: I don't think this is need for Arabidopsis data, but ...
    private static final String CHROMOSOME_PREFIX = "NC_";

    /**
     * Create a new BarNcbiGffGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public BarNcbiGffGFF3RecordHandler (Model model) {
        super(model);
        refsAndCollections.put("Exon", "transcripts");
        refsAndCollections.put("Transcript", "gene");
        refsAndCollections.put("MRNA", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // This method is called for every line of GFF3 file(s) being read.  Features and their
        // locations are already created but not stored so you can make changes here.  Attributes
        // are from the last column of the file are available in a map with the attribute name as
        // the key.   For example:
        //
        //     Item feature = getFeature();
        //     String symbol = record.getAttributes().get("symbol");
        //     feature.setAttribute("symbol", symbol);
        //
        // Any new Items created can be stored by calling addItem().  For example:
        // 
        //     String geneIdentifier = record.getAttributes().get("gene");
        //     gene = createItem("Gene");
        //     gene.setAttribute("primaryIdentifier", geneIdentifier);
        //     addItem(gene);
        //
        // You should make sure that new Items you create are unique, i.e. by storing in a map by
        // some identifier.
	        // only want chromsomes of interest
        if (!record.getSequenceID().startsWith(CHROMOSOME_PREFIX)) {
            /**
             * We have genes on multiple chromosomes. We are only interested in the "good" ones.
             * Thus some genes processed by this parser will not have a location.
             * In this case, we do not want to store the gene. See #1259
             */
            removeFeature();
            return;
        }

	// Skip regions for now. TAIR data does not have regions
	if ("region".equals(record.getType())) {
	    removeFeature();
	    return;
	}

	Item feature = getFeature();
        String type = record.getType();

        if ("gene".equals(type)) {
	    // Set Araport/TAIR gene id
            feature.setClassName("Gene");
            for (String identifier : record.getDbxrefs()) {
                if (identifier.contains("Araport")) {
                    String[] bits = identifier.split(":");
                    feature.setAttribute("primaryIdentifier", bits[1]);
                }
            }

	    // This is gene alias
            String symbol = record.getAttributes().get("Name").iterator().next();
            feature.setAttribute("symbol", symbol);

	    // I dount we have description, but ...
            if (record.getAttributes().get("description") != null) {
                String description = record.getAttributes().get("description").iterator().next();
                feature.setAttribute("briefDescription", description);
            }
        } else if ("transcript".equals(type) || "MRNA".equalsIgnoreCase(type)) {
            if ("MRNA".equalsIgnoreCase(type)) {
                feature.setClassName("MRNA");
            } else {
                feature.setClassName("Transcript");
            }
	    
	    // This sets transcipt id. We could use AGI ID, but let's see how it ges
            String identifier = record.getAttributes().get("transcript_id").iterator().next();
            feature.setAttribute("primaryIdentifier", identifier);

	    // This set product
            if (record.getAttributes().get("product") != null) {
                String description = record.getAttributes().get("product").iterator().next();
                feature.setAttribute("name", description);
            }
        } else if ("exon".equals(type)) {
            feature.setClassName("Exon");
	    // Get this number from ID=exon-NM_099983.2-1;
            String exonID = record.getAttributes().get("ID").iterator().next();
            char exonNumber = exonID.charAt(exonID.length() - 1);
            
	    for (String identifier : record.getDbxrefs()) {
                if (identifier.contains("Araport")) {
                    String[] bits = identifier.split(":");
                    feature.setAttribute("primaryIdentifier", bits[1] + "." + Character.toString(exonNumber));
                }
            }
            
	    // Product is like description
            if (record.getAttributes().get("product") != null) {
                String description = record.getAttributes().get("product").iterator().next();
                feature.setAttribute("name", description);
            }
        } else {

	    // Drop everything else
	    removeFeature();
	    return;
	}
    }
}
