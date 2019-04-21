package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2018 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */


import org.apache.log4j.Logger;
import org.biojava.nbio.core.sequence.DNASequence;
import org.biojava.nbio.core.sequence.template.Sequence;

/**
 * See https://intermine.readthedocs.io/en/latest/database/data-sources/library/fasta/ for details on the FASTA source.
 * This is inspired from: https://github.com/intermine/intermine/blob/dev/bio/sources/fasta/src/main/java/org/intermine/bio/dataconversion/NCBIFastaLoaderTask.java
 * @author Asher
 */
public class BarNcbiFastaConverter extends FastaLoaderTask
{
    protected static final Logger LOG = Logger.getLogger(BarNcbiFastaConverter.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getIdentifier(Sequence bioJavaSequence) {
        String header = ((DNASequence) bioJavaSequence).getOriginalHeader();

        // This part can be improve later on.
        if (header.contains("chromosome 1")) {
            return "Chr1";
        } else if (header.contains("chromosome 2")) {
            return "Chr2";
        } else if (header.contains("chromosome 3")) {
            return "Chr3";
        } else if (header.contains("chromosome 4")) {
            return "Chr4";
        } else if (header.contains("chromosome 5")) {
            return "Chr5";
        } else if (header.contains("mitochondrion")) {
            return "ChrM";
        } else if (header.contains("chloroplast")) {
            return "ChrC";
        } else {
            throw new RuntimeException("The header of this FASTA file are not ready yet. Header: " + header);
        }
    }
}
