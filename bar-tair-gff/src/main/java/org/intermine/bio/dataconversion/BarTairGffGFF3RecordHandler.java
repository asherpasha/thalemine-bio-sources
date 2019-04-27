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
 * A converter/retriever for the BarTairGff dataset via GFF files.
 */

public class BarTairGffGFF3RecordHandler extends GFF3RecordHandler
{

    /**
     * Create a new BarTairGffGFF3RecordHandler for the given data model.
     * @param model the model for which items will be created
     */
    public BarTairGffGFF3RecordHandler (Model model) {
        super(model);
        refsAndCollections.put("Exon", "transcripts");
        refsAndCollections.put("Transcript", "gene");
	refsAndCollections.put("LncRNA", "gene");
        refsAndCollections.put("MRNA", "gene");
	refsAndCollections.put("TransposonFragment", "transposableElement");
        refsAndCollections.put("AntisenseRNA", "gene");
        refsAndCollections.put("AntisenseLncRNA", "gene");
        refsAndCollections.put("MiRNAPrimaryTranscript", "gene");
        refsAndCollections.put("NcRNA", "gene");
        refsAndCollections.put("TRNA", "gene");
	refsAndCollections.put("TranscriptRegion", "gene");
        refsAndCollections.put("PseudogenicTranscript","pseudogene");
        refsAndCollections.put("PseudogenicExon","pseudogenicTranscripts");
        refsAndCollections.put("PseudogenicTRNA","pseudogene");
	refsAndCollections.put("UORF", "gene");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void process(GFF3Record record) {
        // This method is called for every line of GFF3 file(s) being read.  Features and their
        // locations are already created but not stored so you can make changes here.  Attributes
        // are from the last column of the file are available in a map with the attribute name as
        // the key.
        Item feature = getFeature();
        String clsName = feature.getClassName();
	String type = record.getType();
	if ("gene".equals(type)) {
	    // Set Araport/TAIR gene id
            feature.setClassName("Gene");

            String geneID = record.getAttributes().get("ID").iterator().next();
            feature.setAttribute("primaryIdentifier", geneID);

	    // This is gene alias
	    if (record.getAttributes().get("symbol") != null) {
                String symbol = record.getAttributes().get("symbol").iterator().next();
	        feature.setAttribute("symbol", symbol);
	    }

	    // I dount we have description, but ...
            if (record.getAttributes().get("computational_description") != null) {
                String description = record.getAttributes().get("computational_description").iterator().next();
                feature.setAttribute("briefDescription", description);
            }
        } else if ("transcript".equals(type) || "MRNA".equalsIgnoreCase(type)) {
            if ("MRNA".equalsIgnoreCase(type)) {
                feature.setClassName("MRNA");
            } else {
                feature.setClassName("Transcript");
            }
            
	    String mRNAID = record.getAttributes().get("ID").iterator().next();
            feature.setAttribute("primaryIdentifier", mRNAID);
	    
	    // This set product
            if (record.getAttributes().get("Parent") != null) {
                String description = record.getAttributes().get("Parent").iterator().next();
                feature.setAttribute("name", description);
            }
        } else if ("exon".equals(type)) {
            feature.setClassName("Exon");
	    // Get this number from ID=exon-NM_099983.2-1;
            String exonID = record.getAttributes().get("ID").iterator().next();
            feature.setAttribute("primaryIdentifier", exonID);
        } 
    }
}
