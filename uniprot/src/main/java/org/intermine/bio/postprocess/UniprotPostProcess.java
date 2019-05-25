package org.intermine.bio.postprocess;

/*
 * Copyright (C) 2002-2015 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.intermine.bio.util.Constants;
import org.intermine.metadata.ClassDescriptor;
import org.intermine.metadata.CollectionDescriptor;
import org.intermine.metadata.ConstraintOp;
import org.intermine.model.InterMineObject;
import org.intermine.bio.util.PostProcessUtil;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreWriter;
import org.intermine.objectstore.intermine.ObjectStoreInterMineImpl;
import org.intermine.objectstore.query.ConstraintSet;
import org.intermine.objectstore.query.ContainsConstraint;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryCollectionReference;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryFunction;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.objectstore.query.SubqueryConstraint;
import org.intermine.model.bio.Gene;
import org.intermine.model.bio.Protein;
import org.intermine.model.bio.Publication;
import org.intermine.model.bio.Transcript;
import org.intermine.model.bio.Synonym;
import org.intermine.postprocess.PostProcessor;
import org.intermine.util.DynamicUtil;

public class UniprotPostProcess extends PostProcessor
{
    private static final Logger LOG = Logger.getLogger(UniprotPostProcess.class);
    protected ObjectStore os;

    public UniprotPostProcess(ObjectStoreWriter osw) {
        super(osw);
        this.os = osw.getObjectStore();
    }

    @Override
    public void postProcess() throws ObjectStoreException {
        LOG.info("Uniprot Postprocessor has started.");
        try {
            processGenesProteinsPublications();
            processProteinsTranscripts();
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Error during uniprot postrprocessing." + e.getMessage());
        }
        LOG.info("Uniprot Postprocessor has completed.");
    }

    private Query getQueryProteinsTranscripts() throws ObjectStoreException {
        Query q = new Query();

        q.setDistinct(false);

        QueryClass qcProtein = new QueryClass(Protein.class);
        QueryClass qcTranscript = new QueryClass(Transcript.class);

        q.addFrom(qcProtein);
        q.addFrom(qcTranscript);

        q.addToSelect(qcProtein);
        q.addToSelect(qcTranscript);

        ConstraintSet cs = new ConstraintSet(ConstraintOp.AND);

        QueryField proteinIdField = new QueryField(qcProtein, "id");
        SimpleConstraint proteinIdCS =
                new SimpleConstraint(proteinIdField, ConstraintOp.IS_NOT_NULL);
        cs.addConstraint(proteinIdCS);

        QueryCollectionReference proteinsTranscriptsRef =
                new QueryCollectionReference(qcProtein, "transcripts");
        cs.addConstraint(new ContainsConstraint(proteinsTranscriptsRef,
                ConstraintOp.CONTAINS, qcTranscript));
        q.setConstraint(cs);

        return q;
    }

    private Iterator<?> getProteinsTranscriptsIterator(final Query query)
        throws ObjectStoreException {

        ObjectStore os1 = osw.getObjectStore();

        ((ObjectStoreInterMineImpl) os1).precompute(query, Constants.PRECOMPUTE_CATEGORY);
        Results res = os1.execute(query, 5000, true, false, true);

        if (res != null) {
            LOG.info("Proteins Transcripts:" + res.size());
        }
        return res.iterator();
    }

    private void processProteinsTranscripts() throws Exception, ObjectStoreException {
        LOG.info("ProcessProteinsTranscripts has started.");
// not used
//        Exception exception = null;
//        long startTime = System.currentTimeMillis();
//        ClassDescriptor classDesc = osw.getModel().getClassDescriptorByName("Protein");
//        int count = 0;

        Query query = getQueryProteinsTranscripts();
        Iterator<?> iterator = getProteinsTranscriptsIterator(query);
        HashMap<Integer, HashSet<Synonym>> protTranscriptsMap =
                new HashMap<Integer, HashSet<Synonym>>();

        Protein lastProtein = null;

        osw.beginTransaction();
        while (iterator.hasNext()) {
            ResultsRow rr = (ResultsRow) iterator.next();

            Protein thisProtein = (Protein) rr.get(0);
            Transcript transcript = (Transcript) rr.get(1);

            if (lastProtein != null && !(lastProtein.equals(thisProtein))) {
                Collections syns = (Collections) lastProtein.getFieldValue("synonyms");
                syns.addAll(protTranscriptsMap.values());

                Protein tempObject;
                try {
                    tempObject = PostProcessUtil.cloneInterMineObject(lastProtein);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                tempObject.setFieldValue("synonyms", syns);
                osw.store(tempObject);

                lastProtein = thisProtein;
                protTranscriptsMap = new HashMap<Integer, HashSet<Synonym>>();
            }

            Integer proteinId = thisProtein.getId();
            String transcriptPrimaryId = transcript.getPrimaryIdentifier();

            // add Transcript IDs as synonyms of Protein
            Class<?> synonymCls = osw.getModel().getClassDescriptorByName("Synonym").getType();
            Synonym synonym = (Synonym) DynamicUtil.createObject(Collections.singleton(synonymCls));
            synonym.setValue(transcriptPrimaryId);
            synonym.setSubject(thisProtein);
            osw.store(synonym);

            if (protTranscriptsMap.get(proteinId) == null) {
                protTranscriptsMap.put(proteinId, new HashSet<Synonym>());
            }
            protTranscriptsMap.get(proteinId).add(synonym);
        }

        if (lastProtein != null) {
            Collections syns = (Collections) lastProtein.getFieldValue("synonyms");
            syns.addAll(protTranscriptsMap.values());

            Protein tempObject;
            try {
                tempObject = PostProcessUtil.cloneInterMineObject(lastProtein);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            tempObject.setFieldValue("synonyms", syns);
            osw.store(tempObject);
        }

        osw.commitTransaction();
        LOG.info("ProcessProteinsTranscripts completed.");
    }

    private Query getGeneQuerySourceRecordsbyProteins() throws ObjectStoreException {
        // Building Query Gene Source Records To Validate/Transfer Publications from Proteins

        Query outerQuery = new Query();
        // Create Outer Query Constraint
        ConstraintSet outerQueryCS = new ConstraintSet(ConstraintOp.AND);

        outerQuery.setDistinct(true);
        QueryClass qcPub = new QueryClass(Publication.class);
        QueryClass qcGenes = new QueryClass(Gene.class);
        QueryClass qcProtein = new QueryClass(Protein.class);

        // outer query from clause
        outerQuery.addFrom(qcPub);
        outerQuery.addFrom(qcProtein);
        outerQuery.addFrom(qcGenes);

        // outer query select clause
        outerQuery.addToSelect(qcGenes);

        // join to collection
        QueryCollectionReference proteinGenePubCollection =
                new QueryCollectionReference(qcGenes, "proteins");
        outerQueryCS.addConstraint(new ContainsConstraint(
                proteinGenePubCollection, ConstraintOp.CONTAINS, qcProtein));

        QueryCollectionReference proteinsPublCollection =
                new QueryCollectionReference(qcProtein, "publications");
        outerQueryCS.addConstraint(new ContainsConstraint(
                proteinsPublCollection, ConstraintOp.CONTAINS, qcPub));

        outerQuery.setConstraint(outerQueryCS);
        return outerQuery;

    }

    private Iterator<?> getGeneSourceIterator(final Query query) throws ObjectStoreException {

        ObjectStore os1 = osw.getObjectStore();
        ((ObjectStoreInterMineImpl) os1).precompute(query, Constants.PRECOMPUTE_CATEGORY);
        Results res = os1.execute(query, 5000, true, false, true);
        if (res != null) {
            LOG.info("Gene Source: " + res.size());
        }
        return res.iterator();
    }

    private Iterator<?> getPublicationIterator(final Query query) throws ObjectStoreException {

        ObjectStore os1 = osw.getObjectStore();
        ((ObjectStoreInterMineImpl) os1).precompute(query, Constants.PRECOMPUTE_CATEGORY);
        Results res = os1.execute(query, 5000, true, false, true);
        if (res != null) {
            LOG.debug("Publications Result Set Size:" + res.size());
        }
        return res.iterator();
    }

    private void processGenesProteinsPublications() throws Exception, ObjectStoreException {

        Exception exception = null;
//        Set<Gene> set = new HashSet<Gene>();
//        long startTime = System.currentTimeMillis();
        Query query = getGeneQuerySourceRecordsbyProteins();

        Iterator<?> iterator = getGeneSourceIterator(query);

        int count = 0;
        int pubAddedCount = 0;

        osw.beginTransaction();
        while (iterator.hasNext()) {
            ResultsRow item = (ResultsRow) iterator.next();

            Gene gene = (Gene) item.get(0);
            InterMineObject object = (InterMineObject) gene;
            count++;
            Set<Publication> notExistingProteinsPublications = new HashSet<Publication>();
            Set<Publication> existingGenePublications = new HashSet<Publication>();
            existingGenePublications = gene.getPublications();
            notExistingProteinsPublications = getPublications(object);

            String destClassName = "Gene";
            String collectionName = "publications";

            if (notExistingProteinsPublications.size() > 0
                    && !notExistingProteinsPublications.isEmpty()) {

                LOG.info(gene.getPrimaryIdentifier() + " Publications: "
                        + existingGenePublications.size() + ", missing "
                        + notExistingProteinsPublications.size());

                // Attempt to store Gene Publication Collection
                try {
                    for (Publication pubItem : notExistingProteinsPublications) {
                        InterMineObject pubObject = (InterMineObject) pubItem;
                        insertPublicationCollectionField(
                                object, pubObject, destClassName, collectionName);
                    }
                } catch (Exception e) {
                    exception = e;
                } finally {
                    if (exception != null) {
                        LOG.error(gene.getPrimaryIdentifier() + ": "
                                + exception.getMessage() + "; Cause: " + exception.getCause());
                        exception.printStackTrace();
                    } else {
                        pubAddedCount++;
                    }
                }
            }
        }
        osw.commitTransaction();
        LOG.info("Processed " + count + "genes, added " + pubAddedCount + "publications.");
    }

    private Query getNotExistingProteinsPubbyGeneQuery(InterMineObject object) {

        QueryClass qcPub = new QueryClass(Publication.class);
        QueryClass qcOtherGenes = new QueryClass(Gene.class);
        QueryClass qcProtein = new QueryClass(Protein.class);

        Query outerQuery = new Query();

        // outer query constraints
        ConstraintSet outerQueryCS = new ConstraintSet(ConstraintOp.OR);
        outerQuery.setDistinct(true);

        // outer query from clause
        outerQuery.addFrom(qcPub);
        outerQuery.addFrom(qcProtein);
        outerQuery.addFrom(qcOtherGenes);

        // outer query group by clause
        outerQuery.addToGroupBy(new QueryField(qcPub, "id"));

        QueryField qfDate = new QueryField(qcPub, "year");
        outerQuery.addToGroupBy(qfDate);

        // outer query select clause
        outerQuery.addToSelect(qcPub);

        // publication count
        QueryFunction qf = new QueryFunction();
        outerQuery.addToSelect(qf);

        outerQuery.addToSelect(qfDate);

        // Gene subquery

        Query geneSubQuery = new Query();
        QueryClass qcPubGeneSQ = new QueryClass(Publication.class);
        geneSubQuery.alias(qcPubGeneSQ, "geneSQ");
        geneSubQuery.setDistinct(false);
        geneSubQuery.addFrom(qcPubGeneSQ);
        geneSubQuery.addFrom(qcOtherGenes);
        geneSubQuery.addToSelect(qcPubGeneSQ);

        ConstraintSet geneSubSetCS = new ConstraintSet(ConstraintOp.AND);

        // works for a single gene object
        QueryCollectionReference genePubCollection =
                new QueryCollectionReference(object, "publications");
        geneSubSetCS.addConstraint(
                new ContainsConstraint(genePubCollection, ConstraintOp.CONTAINS, qcPubGeneSQ));

        QueryCollectionReference geneClassPubCollection =
                new QueryCollectionReference(qcOtherGenes, "publications");
        geneSubSetCS.addConstraint(
                new ContainsConstraint(geneClassPubCollection, ConstraintOp.CONTAINS, qcPubGeneSQ));

        geneSubQuery.setConstraint(geneSubSetCS);

        outerQueryCS.addConstraint(
                new SubqueryConstraint(qcPub, ConstraintOp.NOT_IN, geneSubQuery));

        ConstraintSet outerQueryMainCS = new ConstraintSet(ConstraintOp.AND);

        QueryField geneIdField = new QueryField(qcOtherGenes, "id");
        QueryValue geneIdValue = new QueryValue(object.getId());
        SimpleConstraint geneIdCS =
                new SimpleConstraint(geneIdField, ConstraintOp.EQUALS, geneIdValue);

        QueryCollectionReference proteinGenePubCollection =
                new QueryCollectionReference(qcOtherGenes, "proteins");
        outerQueryMainCS.addConstraint(
                new ContainsConstraint(proteinGenePubCollection, ConstraintOp.CONTAINS, qcProtein));

        QueryCollectionReference proteinPublCollection =
                new QueryCollectionReference(qcProtein, "publications");
        outerQueryMainCS.addConstraint(
                new ContainsConstraint(proteinPublCollection, ConstraintOp.CONTAINS, qcPub));

        outerQueryMainCS.addConstraint(geneIdCS);
        outerQueryMainCS.addConstraint(outerQueryCS);

        outerQuery.setConstraint(outerQueryMainCS);
        return outerQuery;
    }

    private Set<Publication> getPublications(InterMineObject object) throws Exception {

        Set<Publication> publications = new HashSet<Publication>();
        Exception exception = null;
        Query query = getNotExistingProteinsPubbyGeneQuery(object);
        Iterator<?> iterator = getPublicationIterator(query);

        // long startTime = System.currentTimeMillis();
        // int itemCount = 0;

        try {
            if (query == null) {
                exception = new Exception("Publication Query cannot be null.");
                throw exception;
            }

            while (iterator.hasNext()) {
                ResultsRow item = (ResultsRow) iterator.next();
                Publication pub = (Publication) item.get(0);
                // itemCount++;
                // Object countObject = (Object) item.get(1);
                // Long publicationCount = (Long) countObject;
                publications.add(pub);
            }
        } catch (Exception e) {
            exception = e;
        } finally {
            if (exception != null) {
                LOG.error("Publication Query." + exception.getMessage()
                        + "; Cause: " + exception.getCause());
                throw exception;
            } else {
                LOG.debug("Publications Set Size: " + publications.size());
            }
        }

        return publications;

    }

    private CollectionDescriptor getCollectionDescriptor(
            final String className, final String collectionName) {

        ClassDescriptor classDesc;
        CollectionDescriptor colDesc = null;
        classDesc = osw.getModel().getClassDescriptorByName(className);
//        String classDescAsStr = null;
        Map<String, CollectionDescriptor> collectionDescMap =
                new LinkedHashMap<String, CollectionDescriptor>();

        if (classDesc != null) {
//            classDescAsStr = classDesc.getName();

            Set<CollectionDescriptor> collectionDescGene = classDesc.getAllCollectionDescriptors();

            // Adding Class Collection Descriptors to the Map
            for (CollectionDescriptor item : collectionDescGene) {
// ??
//                boolean manyToManyC = false;
//                if (item.relationType() == CollectionDescriptor.M_N_RELATION) {
//                    manyToManyC = true;
//                }
                collectionDescMap.put(item.getName(), item);
            }
            if (!collectionDescMap.isEmpty() && collectionDescMap.size() > 0) {
                if (collectionDescMap.containsKey(collectionName)) {
                    colDesc = collectionDescMap.get(collectionName);
                }
            }
        }
        return colDesc;
    }

    private void insertPublicationCollectionField(InterMineObject destObject,
            InterMineObject sourceObject,
            final String destClassName, final String collectionName) throws Exception {

        Exception exception = null;
        String errorMessage = null;
        try {
            CollectionDescriptor collectionDesc =
                    getCollectionDescriptor(destClassName, collectionName);
            ClassDescriptor classDesc = osw.getModel().getClassDescriptorByName(destClassName);

            // if this is a many to many collection we can use
            // ObjectStore.addToCollection which will
            // write directly to the database.
            boolean manyToMany = false;
            if (collectionDesc == null) {
                errorMessage = "Cannot find collection " + collectionName
                        + " for the class " + destClassName;
                exception = new Exception(errorMessage);
                LOG.error(errorMessage);
                throw exception;
            }

            if (collectionDesc.relationType() == CollectionDescriptor.M_N_RELATION) {
                manyToMany = true;
            }

            if (manyToMany) {
                osw.addToCollection(destObject.getId(), classDesc.getType(),
                        collectionName, sourceObject.getId());
            } else { // publications will be always many to many
                // InterMineObject tempObject =
                // PostProcessUtil.cloneInterMineObject(destObject);
                // tempObject.setFieldValue(collectionName, collection);
                // osw.store(tempObject);
            }
        } catch (Exception e) {
            exception = e;
        } finally {

            if (exception != null) {
                exception.printStackTrace();
                LOG.error("Persistence of collection for object: " + destObject.toString()
                        + "; Collection Name: " + collectionName + "; Message:"
                        + exception.getMessage() + "; Cause:" + exception.getCause());
                throw exception;
            } else {
                LOG.debug("Element of " + collectionName + " stored. Dest Object:"
                        + destObject.toString() + "; Source Object:" + sourceObject.toString());
            }
        }
    }
}
