package dws.uni.mannheim.semantic_complexity.spreading_activation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.neo4j.graphdb.DynamicLabel;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.tooling.GlobalGraphOperations;

import dws.uni.mannheim.semantic_complexity.CoherenceMetrics;
import dws.uni.mannheim.semantic_complexity.FeaturedDocument;
import dws.uni.mannheim.semantic_complexity.LinkedDocument;
import dws.uni.mannheim.semantic_complexity.Mention;
import dws.uni.mannheim.semantic_complexity.Paragraph;
import dws.uni.mannheim.semantic_complexity.Sentence;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

public class SAComplexityModes
{

    final String TEST_MODE = "GRAPHDECAY0.5FIRE0.0025READINGDECAYTOKEN1.0SENT1.0PAR1.0IfalseEfalse";

    GraphDatabaseService db;

    // final static double FORGET_THRESHOLD = 10E-6;
    static double DEGREE_LOG_NORMALIZATION;

    public SAComplexityModes(GraphDatabaseService db)
    {
        this.db = db;
        GlobalGraphOperations op = GlobalGraphOperations.at(this.db);
        int count = 0;
        ResourceIterator<Node> it = op.getAllNodes().iterator();
        while (it.hasNext())
        {
            count++;
            it.next();
        }
        DEGREE_LOG_NORMALIZATION = log2(count - 1);

    }

    private double log2(double x)
    {
        return 1.0 * Math.log(x) / (1.0 * Math.log(2));
    }

    private Map<Long, Map<String, Double>> runSpreadingActivation(
            LinkedDocument fdoc, Node seed, Map<String, Mode> modesIndex)
    {

        Map<Long, Map<String, Double>> tidalActivation = new HashMap<>();
        tidalActivation.put(seed.getId(), new HashMap<>());

        Map<Long, Set<String>> fired = new HashMap<>();
        Map<Long, Set<String>> burned = new HashMap<>();
        fired.put(seed.getId(), new HashSet<>());
        burned.put(seed.getId(), new HashSet<>());

        for (String mode : modesIndex.keySet())
        {
            tidalActivation.get(seed.getId()).put(mode, 1.0);
            fired.get(seed.getId()).add(mode);
        }
        boolean first = true;

        int firingRound = 0;
        while (fired.size() > 0)
        {

            firingRound++;
            System.out.println("firing round " + firingRound + " . elems : "
                    + fired.size());
            // System.out.println( fdoc.getLdoc().docPath + ": Firing round :" +
            // firingRound + ". Elems to fire:" + fired.size());

            updateTidalActivation(tidalActivation, fired, burned, modesIndex,
                    first);
            // System.out.println("Activations: ");
            // for (Entry<Long, Map<String, Double>> tae:
            // tidalActivation.entrySet()) {
            // System.out.println(this.db.getNodeById(tae.getKey()).getProperty("uri")
            // +"--> "+tae.getValue().get(TEST_MODE));
            // }

            for (Long n : fired.keySet())
            {
                if (burned.containsKey(n))
                {
                    burned.get(n).addAll(fired.get(n));
                } else
                    burned.put(n, fired.get(n));
            }

            fired.clear();
            for (Entry<Long, Map<String, Double>> e : tidalActivation
                    .entrySet())
            {
                for (Entry<String, Double> me : e.getValue().entrySet())
                {
                    if (me.getValue() > modesIndex.get(me.getKey())
                            .getFiringThreshold()
                            && ((!burned.containsKey(e.getKey())) || (burned
                                    .containsKey(e.getKey()) && !burned.get(
                                    e.getKey()).contains(me.getKey()))))
                    {
                        if (fired.containsKey(e.getKey()))
                            fired.get(e.getKey()).add(me.getKey());
                        else
                        {
                            fired.put(e.getKey(), new HashSet<String>());
                            fired.get(e.getKey()).add(me.getKey());
                        }
                    }
                }

            }
            first = false;
        }
        return tidalActivation;

    }

    public DescriptiveStatistics toDescriptiveStats(Map<Mention, Double> vals)
    {
        DescriptiveStatistics result = new DescriptiveStatistics();
        for (double v : vals.values())
        {
            result.addValue(v);
        }
        return result;
    }

    public CoherenceMetrics computeComplexityWithSpreadingActivationOncePerEntity(
            LinkedDocument fdoc, Map<String, Mode> modesIndex, boolean allOnes,
            Map<String, Double> avgActivationsAtEncounter,
            Map<String, Double> avgActivationAtEOS,
            Map<String, Double> avgActivationAtEOP,
            Map<String, Double> avgActivationAtEODoc, Jedis jedis)
    {

        Map<String, Map<Mention, Double>> activationsAtEncounter = new HashMap<>();
        Map<String, Map<Mention, Double>> activationAtEOS = new HashMap<>();
        Map<String, Map<Mention, Double>> activationAtEOP = new HashMap<>();
        Map<String, Map<Mention, Double>> activationAtEODoc = new HashMap<>();

        this.readWithSpreadingActivationOncePerEntityJedis(fdoc, modesIndex,
                jedis);
        // System.out.println(fdoc.getPath()
        // +" ALL ENTITIES OF DOC Done, so NOW AGGREGATING .... ");
        CoherenceMetrics metrics = this.aggreagateSpreadingActivationsOncePerEntityJedis(fdoc, modesIndex,
                allOnes, activationsAtEncounter, activationAtEOS,
                activationAtEOP, activationAtEODoc, jedis);

        // System.out.println(fdoc.getPath() +
        // ":   AGGREGATION FINISHED, now computing the stats");
        // System.out.println("---Activations at encounter----------------------------");
        for (String m : activationsAtEncounter.keySet())
        {
            // System.out.println("MODE: " + m);
            for (Mention me : activationsAtEncounter.get(m).keySet())
            {
                // System.out.println(me.getMentionedConcept() + " - > " +
                // activationsAtEncounter.get(m).get(me));
            }
            DescriptiveStatistics stats = this
                    .toDescriptiveStats(activationsAtEncounter.get(m));
            avgActivationsAtEncounter.put(m, stats.getMean());
            // System.out.println("MODE MEAN ------------------------");
            // System.out.println(stats.getMean());
        }
        // System.out.println("STATS Activation At Encounter: +++++++++++++++++++++++++++++++");
        // System.out.println(avgActivationsAtEncounter);

        for (String m : activationAtEOS.keySet())
        {
            DescriptiveStatistics stats = this
                    .toDescriptiveStats(activationAtEOS.get(m));
            avgActivationAtEOS.put(m, stats.getMean());
        }

        for (String m : activationAtEOP.keySet())
        {
            DescriptiveStatistics stats = this
                    .toDescriptiveStats(activationAtEOP.get(m));
            avgActivationAtEOP.put(m, stats.getMean());
        }

        for (String m : activationAtEODoc.keySet())
        {
            DescriptiveStatistics stats = this
                    .toDescriptiveStats(activationAtEODoc.get(m));
            avgActivationAtEODoc.put(m, stats.getMean());
        }

        return metrics;
    }

    Map<Long, Map<String, Double>> readActivationFromFile(String filename)
            throws Exception
    {
        // read object from file
        FileInputStream fis = new FileInputStream(filename);
        ObjectInputStream ois = new ObjectInputStream(fis);
        Map<Long, Map<String, Double>> result = (Map<Long, Map<String, Double>>) ois
                .readObject();
        ois.close();
        return result;
    }

    private CoherenceMetrics aggreagateSpreadingActivationsOncePerEntityJedis(
            LinkedDocument fdoc, Map<String, Mode> modesIndex, boolean allOnes,
            Map<String, Map<Mention, Double>> activationsAtEncounter,
            Map<String, Map<Mention, Double>> activationAtEOS,
            Map<String, Map<Mention, Double>> activationAtEOP,
            Map<String, Map<Mention, Double>> activationAtEODoc, Jedis jedis)
    {

        // Initialize coherence metrics
        CoherenceMetrics metrics = new CoherenceMetrics();

        // initializing the result containers;
        Set<Long> allActivatedNodes = new HashSet<>();
        Set<String> uniqueEntities = new HashSet<>();
        Map<String, Integer> entityMentionCount = new HashMap<>();
        Set<Long> nodeDegreesSum = new HashSet<>();
        double exclusivitySum = 0.0;
        int exclusivityCount = 0;

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
                uniqueEntities.add(me.getMentionedConcept());
                entityMentionCount.put(me.getMentionedConcept(),
                    entityMentionCount.getOrDefault(me.getMentionedConcept(), 0) + 1);
            }
            activationsAtEncounter.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEOS.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEOP.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEODoc.put(m, mentionResults);
        }

        // Map<Long, Map<String,Double>> aggregatedActivation = new HashMap<>();
        // Map<String, Map<Long, Map<String, Double>>> saPerEntity = new
        // HashMap<>();

        int prevMentionTokenOffsetEnd = -1;
        int prevMentionSentenceIndex = 0;
        int prevMentionParagraphIndex = 0;
        int currentParagraph = 0;
        int currentSentence = 0;

        // Distance tracking for coherence
        List<Integer> tokenDistances = new ArrayList<>();
        List<Integer> sentenceDistances = new ArrayList<>();
        List<Integer> paragraphDistances = new ArrayList<>();
        Map<String, Integer> entityFirstMentionSentence = new HashMap<>();
        Map<String, Integer> entityFirstMentionParagraph = new HashMap<>();
        Set<Integer> sentencesWithEntities = new HashSet<>();
        Set<Integer> paragraphsWithEntities = new HashSet<>();

        Map<Mention, Map<String, Double>> currentAggregatedActivations = new HashMap<>();

        for (Mention m : fdoc.getMentions())
        {
            Map<String, Double> modeActivations = new HashMap<>();
            for (String mode : modesIndex.keySet())
            {
                modeActivations.put(mode, 0.0);
            }
            currentAggregatedActivations.put(m, modeActivations);
        }

        // Pairwise entity relatedness tracking (document-level)
        // Map: entityURI1 -> entityURI2 -> activation strength
        Map<String, Map<String, Double>> pairwiseRelatedness = new HashMap<>();
        Map<String, Double> maxActivationPerEntity = new HashMap<>();

        // Initialize for all unique entities
        for (String entityUri : uniqueEntities) {
            pairwiseRelatedness.put(entityUri, new HashMap<>());
            maxActivationPerEntity.put(entityUri, 0.0);
        }

        // Paragraph-level pairwise relatedness tracking
        // Map: paragraphIndex -> (entityURI1 -> entityURI2 -> activation strength)
        Map<Integer, Map<String, Map<String, Double>>> pairwiseRelatednessPerParagraph = new HashMap<>();
        // Map: paragraphIndex -> entityURI -> max activation in that paragraph
        Map<Integer, Map<String, Double>> maxActivationPerEntityPerParagraph = new HashMap<>();
        // Map: paragraphIndex -> set of entity URIs in that paragraph
        Map<Integer, Set<String>> entitiesPerParagraph = new HashMap<>();

        try
        {
            // initialise normalising constant per mode
            // Map<String, Double> normalisingConstantPerMode = new HashMap<>();
            // for(String mode: modesIndex.keySet()) {
            // normalisingConstantPerMode.put(mode, 0.0);
            // }

            for (Paragraph p : fdoc.getParagraphs())
            {
                for (Sentence s : p.getSentences())
                {
                    List<Mention> mentions = s.getOrderedMentions();
                    // activation at encounter
                    for (int i = 0; i < mentions.size(); i++)
                    { // iterate through all mentions to activate them in a
                      // reading simulating fashion
                        Mention mention = mentions.get(i);
                        Label res = DynamicLabel.label("Resource");
                        Node seed = this.db.findNode(res, "uri",
                                mention.getMentionedConcept());
                        double seedImportance = log2(seed.getDegree() + 1)
                                / DEGREE_LOG_NORMALIZATION;

                        // Track node degree for metrics
                        nodeDegreesSum.add((long) seed.getDegree());

                        // Track sentence and paragraph coverage
                        sentencesWithEntities.add(currentSentence);
                        paragraphsWithEntities.add(currentParagraph);

                        // Track entity persistence across paragraphs
                        String entityConcept = mention.getMentionedConcept();
                        if (!entityFirstMentionParagraph.containsKey(entityConcept)) {
                            entityFirstMentionParagraph.put(entityConcept, currentParagraph);
                        }
                        if (!entityFirstMentionSentence.containsKey(entityConcept)) {
                            entityFirstMentionSentence.put(entityConcept, currentSentence);
                        }

                        // Track entities per paragraph for paragraph-level pairwise relatedness
                        if (!entitiesPerParagraph.containsKey(currentParagraph)) {
                            entitiesPerParagraph.put(currentParagraph, new HashSet<>());
                            pairwiseRelatednessPerParagraph.put(currentParagraph, new HashMap<>());
                            maxActivationPerEntityPerParagraph.put(currentParagraph, new HashMap<>());
                        }
                        entitiesPerParagraph.get(currentParagraph).add(entityConcept);

                        // System.out.println("Reading mention : " +i + " - " +
                        // mention.getMentionedConcept());

                        if (prevMentionTokenOffsetEnd > -1)
                        { // if we are not on the first mention, first apply
                          // reading decay to all the existing activations in
                          // the memory.
                          // System.out.println(fdoc.getLdoc().docPath
                          // +" : applying reading decay with values...");

                            int deltaToken = mentions.get(i)
                                    .getTokenOffsetEnd()
                                    - prevMentionTokenOffsetEnd; // distanta o
                                                                 // punem sa se
                                                                 // calculeze de
                                                                 // cand se
                                                                 // termina
                                                                 // mentiunea,
                            // in ideea ca entitatea devine activa in cap dupa
                            // ce a fost citita tot numele.
                            // In felul asta, entitatile mai lungi devin natural
                            // mai putin active in memorie.
                            if (deltaToken < 0)
                                deltaToken = 0; // this can happen due to
                                                // puctuation or titles and
                                                // other noise in text

                            // Track distances for coherence metrics
                            tokenDistances.add(deltaToken);

                            // System.out.println("delta token: " + deltaToken);
                            int deltaSentence = currentSentence
                                    - prevMentionSentenceIndex;

                            sentenceDistances.add(deltaSentence);

                            // System.out.println("delta sentence: " +
                            // deltaSentence);
                            int deltaParagraph = currentParagraph
                                    - prevMentionParagraphIndex;

                            paragraphDistances.add(deltaParagraph);

                            // System.out.println("delta paragraph: " +
                            // deltaParagraph);

                            // first we update all the activations, as they fade
                            // due to reading decay.
                            // System.out.println("===Current Aggregated Activations");
                            for (Mention m : currentAggregatedActivations
                                    .keySet())
                            {
                                // System.out.println("********* MENTION: " +
                                // m.toString());
                                Map<String, Double> modeActivations = currentAggregatedActivations
                                        .get(m);
                                // System.out.println("BEFORE DECAYS:");
                                // System.out.println(modeActivations.get(TEST_MODE));
                                for (String mode : modeActivations.keySet())
                                {
                                    modeActivations
                                            .put(mode,
                                                    modeActivations.get(mode)
                                                            * Math.pow(
                                                                    modesIndex
                                                                            .get(mode)
                                                                            .getReadingTokenDecay(),
                                                                    deltaToken)
                                                            * Math.pow(
                                                                    modesIndex
                                                                            .get(mode)
                                                                            .getReadingSentenceDecay(),
                                                                    deltaSentence)
                                                            * Math.pow(
                                                                    modesIndex
                                                                            .get(mode)
                                                                            .getReadingParagraphDecay(),
                                                                    deltaParagraph));
                                }
                                // System.out.println("AFTER DECAYS:");
                                // System.out.println(modeActivations.get(TEST_MODE));
                                currentAggregatedActivations.put(m,
                                        modeActivations);
                            }

                            // update the normalising constants
                            // for (String mode:
                            // normalisingConstantPerMode.keySet()) {
                            // normalisingConstantPerMode.put(mode,
                            // normalisingConstantPerMode.get(mode)
                            // *
                            // Math.pow(modesIndex.get(mode).getReadingTokenDecay(),
                            // deltaToken)
                            // *
                            // Math.pow(modesIndex.get(mode).getReadingSentenceDecay(),
                            // deltaSentence)
                            // *
                            // Math.pow(modesIndex.get(mode).getReadingParagraphDecay(),
                            // deltaParagraph));
                            // }

                            // System.out.println(fdoc.getLdoc().docPath
                            // +" : reading decay applied");

                            // second, we check if the current mention has
                            // already been activated, and if so, we set its
                            // activation at encounter.

                            // System.out.println("ACTIVATION AT ENCOUNTER OF CURRENT MENTION: "
                            // + mention.toString());
                            Map<String, Double> activationRes = currentAggregatedActivations
                                    .get(mention);
                            // System.out.println(activationRes);
                            for (Entry<String, Double> e : activationRes
                                    .entrySet())
                            {
                                if (activationsAtEncounter.containsKey(e
                                        .getKey())
                                        && activationsAtEncounter.get(e
                                                .getKey()) != null)
                                {
                                    activationsAtEncounter.get(e.getKey()).put(
                                            mention, e.getValue());

                                    // activationsAtEncounter.get(e.getKey()).put(mention,
                                    // e.getValue() == 0.0 ? 0.0 :
                                    // e.getValue()/normalisingConstantPerMode.get(e.getKey()));
                                    // System.out.println(e.getKey() +
                                    // " -> BEFORE :"+ e.getValue() +
                                    // "   AFTER: " +
                                    // String.valueOf(e.getValue()/normalisingConstantPerMode.get(e.getKey())));

                                } else
                                {
                                    System.out
                                            .println("activations at encounter does not contain mode : "
                                                    + e.getKey());
                                    System.out
                                            .println("activations at encounter size : "
                                                    + activationsAtEncounter
                                                            .size());
                                    System.out.println("activationRes size : "
                                            + activationRes.size());
                                }
                            }

                        }
                        // update the indexes to the current ones
                        // System.out.println("prev token :" +
                        // prevMentionTokenOffsetEnd + " . current token : "
                        // +mentions.get(i).getTokenOffsetEnd() );
                        prevMentionTokenOffsetEnd = mentions.get(i)
                                .getTokenOffsetEnd();
                        // System.out.println("prev sentence :" +
                        // prevMentionSentenceIndex + " . current sentence : "
                        // +currentSentence );
                        prevMentionSentenceIndex = currentSentence;
                        // System.out.println("prev paragraph :" +
                        // prevMentionParagraphIndex + " . current paragraph : "
                        // +currentParagraph );
                        prevMentionParagraphIndex = currentParagraph;

                        // third, we applying the tidal activation from the
                        // current mention

                        // System.out.println(fdoc.getLdoc().docPath
                        // +" : Aggregating the activations from mention: " +
                        // i);
                        // System.out.println(fdoc.getPath() +
                        // ": Getting activations of "+ seed.getId() +": "
                        // +mention.getMentionedConcept() +" from REDIS");
                        try
                        {
                            for (Entry<Mention, Map<String, Double>> mentionCurrAct : currentAggregatedActivations
                                    .entrySet())
                            {
                                Node mention2 = this.db.findNode(res, "uri",
                                        mentionCurrAct.getKey()
                                                .getMentionedConcept());
                                if (jedis.exists(seed.getId() + "->"
                                        + mention2.getId()))
                                {
                                    allActivatedNodes.add(mention2.getId());
                                    Map<String, String> freshTidalActivations = jedis
                                            .hgetAll(seed.getId() + "->"
                                                    + mention2.getId());
                                    // System.out.println("Jedis entry retrieved: "
                                    // + mention.getMentionedConcept() +"->"+
                                    // mentionCurrAct.getKey().getMentionedConcept());
                                    Map<String, Double> modeValues = mentionCurrAct
                                            .getValue();
                                    // System.out.println("with values: ");
                                    // System.out.println(freshTidalActivations);
                                    for (String mode : modeValues.keySet())
                                    {
                                        Double currValue = modeValues.get(mode);
                                        Double freshValue = 0.0;
                                        if (freshTidalActivations
                                                .containsKey(modesIndex
                                                        .get(mode).graphSetting
                                                        .getSettingName()))
                                        {
                                            freshValue = Double
                                                    .valueOf(freshTidalActivations.get(modesIndex
                                                            .get(mode).graphSetting
                                                            .getSettingName()));
                                            if (allOnes)
                                            {
                                                if (freshValue > 0.0
                                                        && freshValue < 1.0)
                                                    freshValue = 1.0;
                                            } else
                                            {
                                                if (freshValue == 1.0)
                                                    freshValue = seedImportance;
                                            }
                                            currValue = currValue + freshValue;
                                            currentAggregatedActivations.get(
                                                    mentionCurrAct.getKey())
                                                    .put(mode, currValue);

                                            // Track pairwise entity relatedness (document-level)
                                            // Only track if target is also a seed entity (from uniqueEntities)
                                            String sourceEntity = mention.getMentionedConcept();
                                            String targetEntity = mentionCurrAct.getKey().getMentionedConcept();
                                            if (uniqueEntities.contains(targetEntity) && !sourceEntity.equals(targetEntity)) {
                                                // Record cross-activation from source to target (document-level)
                                                double existingActivation = pairwiseRelatedness.get(sourceEntity)
                                                    .getOrDefault(targetEntity, 0.0);
                                                pairwiseRelatedness.get(sourceEntity).put(targetEntity,
                                                    Math.max(existingActivation, freshValue));

                                                // Track paragraph-level pairwise relatedness
                                                // Only track if both entities are in the same paragraph
                                                if (entitiesPerParagraph.get(currentParagraph).contains(sourceEntity) &&
                                                    entitiesPerParagraph.get(currentParagraph).contains(targetEntity)) {
                                                    // Initialize source entity map if needed
                                                    if (!pairwiseRelatednessPerParagraph.get(currentParagraph).containsKey(sourceEntity)) {
                                                        pairwiseRelatednessPerParagraph.get(currentParagraph).put(sourceEntity, new HashMap<>());
                                                    }
                                                    // Record cross-activation for this paragraph
                                                    double existingParaActivation = pairwiseRelatednessPerParagraph.get(currentParagraph)
                                                        .get(sourceEntity).getOrDefault(targetEntity, 0.0);
                                                    pairwiseRelatednessPerParagraph.get(currentParagraph).get(sourceEntity).put(targetEntity,
                                                        Math.max(existingParaActivation, freshValue));
                                                }
                                            }
                                        }
                                    }
                                } else
                                {
                                    // System.out.println("No Redis entry found for: "
                                    // + seed.getId()+"->"+mention2.getId());
                                }
                            }
                        } catch (Exception ex)
                        {
                            ex.printStackTrace();
                        }
                        // update normalising constants
                        // for (String mode:
                        // normalisingConstantPerMode.keySet()) {
                        // normalisingConstantPerMode.put(mode,
                        // normalisingConstantPerMode.get(mode)+1.0);
                        // }

                    }

                    // System.out.println("Applying decays at end of sentence");
                    // applying decays at the end of sentence
                    if (prevMentionTokenOffsetEnd > -1)
                    {
                        int deltaToken = s.getTokenOffsetEnd()
                                - prevMentionTokenOffsetEnd; // distanta o punem
                                                             // sa se calculeze
                                                             // de cand se
                                                             // termina
                                                             // mentiunea,
                        // in ideea ca entitatea devine activa in cap dupa ce a
                        // fost citita tot numele.
                        // In felul asta, entitatile mai lungi devin natural mai
                        // putin active in memorie.
                        if (deltaToken < 0)
                            deltaToken = 0; // this can happen due to puctuation
                                            // or titles.
                        // System.out.println("delta token: " + deltaToken);
                        int deltaSentence = currentSentence
                                - prevMentionSentenceIndex;
                        // System.out.println("delta sentence: " +
                        // deltaSentence);
                        int deltaParagraph = currentParagraph
                                - prevMentionParagraphIndex;
                        // System.out.println("delta paragraph: " +
                        // deltaParagraph);

                        // first we update all the activations, as they fade due
                        // to reading decay.
                        // System.out.println("===Current Aggregated Activations");
                        for (Mention m : currentAggregatedActivations.keySet())
                        {
                            // System.out.println("********* MENTION: " +
                            // m.toString());
                            Map<String, Double> modeActivations = currentAggregatedActivations
                                    .get(m);
                            // System.out.println("BEFORE DECAYS:");
                            // System.out.println(modeActivations.get(TEST_MODE));
                            for (String mode : modeActivations.keySet())
                            {
                                modeActivations
                                        .put(mode,
                                                modeActivations.get(mode)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingTokenDecay(),
                                                                deltaToken)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingSentenceDecay(),
                                                                deltaSentence)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingParagraphDecay(),
                                                                deltaParagraph));
                            }
                            // System.out.println("AFTER DECAYS:");
                            // System.out.println(modeActivations.get(TEST_MODE));
                            currentAggregatedActivations
                                    .put(m, modeActivations);
                        }

                        // decay the normalising constants
                        // for (String mode:
                        // normalisingConstantPerMode.keySet()) {
                        // normalisingConstantPerMode.put(mode,
                        // normalisingConstantPerMode.get(mode)
                        // *
                        // Math.pow(modesIndex.get(mode).getReadingTokenDecay(),
                        // deltaToken)
                        // *
                        // Math.pow(modesIndex.get(mode).getReadingSentenceDecay(),
                        // deltaSentence)
                        // *
                        // Math.pow(modesIndex.get(mode).getReadingParagraphDecay(),
                        // deltaParagraph));
                        // }

                    }
                    // iterate through mentions to compute activation at end of
                    // sentence.
                    //System.out.println("Applying aggregations at end of sentence. ");
                    for (Mention m : s.getMentions())
                    {
                        Map<String, Double> activationRes = currentAggregatedActivations
                                .get(m);
                        for (Entry<String, Double> e : activationRes.entrySet())
                        {
                            activationAtEOS.get(e.getKey())
                                    .put(m, e.getValue());
                            // activationAtEOS.get(e.getKey()).put(m,
                            // e.getValue()==0.0 ? 0.0 :
                            // e.getValue()/normalisingConstantPerMode.get(e.getKey()));

                            // Track maximum activation for each entity (document-level)
                            String entityUri = m.getMentionedConcept();
                            double currentMax = maxActivationPerEntity.getOrDefault(entityUri, 0.0);
                            maxActivationPerEntity.put(entityUri, Math.max(currentMax, e.getValue()));

                            // Track maximum activation per entity per paragraph
                            double currentParaMax = maxActivationPerEntityPerParagraph.get(currentParagraph)
                                .getOrDefault(entityUri, 0.0);
                            maxActivationPerEntityPerParagraph.get(currentParagraph).put(entityUri,
                                Math.max(currentParaMax, e.getValue()));
                        }
                    }
                    currentSentence++;
                    if (prevMentionTokenOffsetEnd > -1)
                        prevMentionTokenOffsetEnd = s.getTokenOffsetEnd();
                }

                // iterate through mentions to compute activation at end of
                // paragraph.
                // there is no need to decay here as well, as all paragraphs
                // finish when a sentence finished, so the decay has been
                // achieved by the last sentence of the paragraph.
                //System.out.println("Applying aggregations at end of paragraph. ");
                for (Mention m : p.getMentions())
                {
                    Map<String, Double> activationRes = currentAggregatedActivations
                            .get(m);
                    for (Entry<String, Double> e : activationRes.entrySet())
                    {
                        activationAtEOP.get(e.getKey()).put(m, e.getValue());

                        // activationAtEOP.get(e.getKey()).put(m, e.getValue()
                        // ==0.0 ? 0.0 :
                        // e.getValue()/normalisingConstantPerMode.get(e.getKey()));
                    }
                }
                currentParagraph++;
            }

            // iterate through mentions to compute activation at end of
            // document.
            //System.out.println("Applying aggregations at end of document. ");
            for (Mention m : fdoc.getMentions())
            {
                Map<String, Double> activationRes = currentAggregatedActivations
                        .get(m);
                for (Entry<String, Double> e : activationRes.entrySet())
                {
                    activationAtEODoc.get(e.getKey()).put(m, e.getValue());
                    // activationAtEODoc.get(e.getKey()).put(m, e.getValue() ==
                    // 0? 0.0 :
                    // e.getValue()/normalisingConstantPerMode.get(e.getKey()));
                }
            }

        } catch (Exception ex)
        {
            ex.printStackTrace();
        }

        // Populate coherence metrics from collected data
        metrics.activatedNodeCount = allActivatedNodes.size();
        metrics.uniqueEntityCount = uniqueEntities.size();
        metrics.totalMentionCount = fdoc.getMentions().size();

        // Entity repetition ratio
        if (metrics.uniqueEntityCount > 0) {
            metrics.entityRepetitionRatio = (double) metrics.totalMentionCount / metrics.uniqueEntityCount;
        }

        // Node degree statistics
        if (!nodeDegreesSum.isEmpty()) {
            long degreeSum = 0;
            for (Long degree : nodeDegreesSum) {
                degreeSum += degree;
            }
            metrics.avgNodeDegree = (double) degreeSum / nodeDegreesSum.size();
        }

        // Distance metrics
        if (!tokenDistances.isEmpty()) {
            int sum = 0;
            int max = 0;
            for (Integer dist : tokenDistances) {
                sum += dist;
                if (dist > max) max = dist;
            }
            metrics.avgTokenDistance = (double) sum / tokenDistances.size();
            metrics.maxTokenDistance = max;

            // Compute standard deviation
            double variance = 0.0;
            for (Integer dist : tokenDistances) {
                variance += Math.pow(dist - metrics.avgTokenDistance, 2);
            }
            metrics.stdTokenDistance = Math.sqrt(variance / tokenDistances.size());
        }

        if (!sentenceDistances.isEmpty()) {
            int sum = 0;
            int max = 0;
            for (Integer dist : sentenceDistances) {
                sum += dist;
                if (dist > max) max = dist;
            }
            metrics.avgSentenceDistance = (double) sum / sentenceDistances.size();
            metrics.maxSentenceDistance = max;

            // Compute standard deviation
            double variance = 0.0;
            for (Integer dist : sentenceDistances) {
                variance += Math.pow(dist - metrics.avgSentenceDistance, 2);
            }
            metrics.stdSentenceDistance = Math.sqrt(variance / sentenceDistances.size());
        }

        if (!paragraphDistances.isEmpty()) {
            int sum = 0;
            int max = 0;
            for (Integer dist : paragraphDistances) {
                sum += dist;
                if (dist > max) max = dist;
            }
            metrics.avgParagraphDistance = (double) sum / paragraphDistances.size();
            metrics.maxParagraphDistance = max;

            // Compute standard deviation
            double variance = 0.0;
            for (Integer dist : paragraphDistances) {
                variance += Math.pow(dist - metrics.avgParagraphDistance, 2);
            }
            metrics.stdParagraphDistance = Math.sqrt(variance / paragraphDistances.size());
        }

        // Text structure
        metrics.sentenceCount = fdoc.getSentences().size();
        metrics.paragraphCount = fdoc.getParagraphs().size();
        // Approximate token count from full text
        String fullText = fdoc.getText();
        if (fullText != null && !fullText.isEmpty()) {
            metrics.tokenCount = fullText.split("\\W+").length;
        }

        if (metrics.sentenceCount > 0) {
            metrics.avgSentenceLength = (double) metrics.tokenCount / metrics.sentenceCount;
            metrics.entitiesPerSentence = (double) metrics.totalMentionCount / metrics.sentenceCount;
        }

        if (metrics.paragraphCount > 0) {
            metrics.avgParagraphLength = (double) metrics.sentenceCount / metrics.paragraphCount;
            metrics.entitiesPerParagraph = (double) metrics.totalMentionCount / metrics.paragraphCount;
        }

        if (metrics.tokenCount > 0) {
            metrics.entityDensity = (double) metrics.totalMentionCount / metrics.tokenCount;
        }

        // Coverage ratios
        metrics.sentencesWithEntities = sentencesWithEntities.size();
        metrics.paragraphsWithEntities = paragraphsWithEntities.size();
        if (metrics.sentenceCount > 0) {
            metrics.sentenceCoverageRatio = (double) metrics.sentencesWithEntities / metrics.sentenceCount;
        }
        if (metrics.paragraphCount > 0) {
            metrics.paragraphCoverageRatio = (double) metrics.paragraphsWithEntities / metrics.paragraphCount;
        }

        // Remention and persistence metrics
        metrics.rementionCount = 0;
        for (Integer count : entityMentionCount.values()) {
            if (count > 1) {
                metrics.rementionCount += (count - 1);
            }
        }

        if (metrics.uniqueEntityCount > 0) {
            metrics.avgRementionsPerEntity = (double) metrics.rementionCount / metrics.uniqueEntityCount;
        }

        // Entity persistence (entities appearing in multiple paragraphs)
        Map<String, Set<Integer>> entityParagraphs = new HashMap<>();
        for (dws.uni.mannheim.semantic_complexity.Mention m : fdoc.getMentions()) {
            Integer paragraphIndex = fdoc.getMapConceptToParagraph().get(m);
            if (paragraphIndex != null) {
                entityParagraphs.computeIfAbsent(m.getMentionedConcept(), k -> new HashSet<>()).add(paragraphIndex);
            }
        }
        for (Set<Integer> paragraphs : entityParagraphs.values()) {
            if (paragraphs.size() > 1) {
                metrics.entityPersistence++;
            }
        }

        // Carried forward entities (entities continuing from one paragraph to next)
        if (metrics.paragraphCount > 1) {
            for (int i = 0; i < metrics.paragraphCount - 1; i++) {
                Set<String> currentParEntities = new HashSet<>();
                Set<String> nextParEntities = new HashSet<>();

                for (Map.Entry<String, Set<Integer>> entry : entityParagraphs.entrySet()) {
                    if (entry.getValue().contains(i)) currentParEntities.add(entry.getKey());
                    if (entry.getValue().contains(i + 1)) nextParEntities.add(entry.getKey());
                }

                // Count entities that appear in both consecutive paragraphs
                for (String entity : currentParEntities) {
                    if (nextParEntities.contains(entity)) {
                        metrics.carriedForwardEntities++;
                    }
                }
            }
        }

        // Compute activation statistics from EOS activations (per mention)
        for (String mode : activationAtEOS.keySet()) {
            Map<Mention, Double> eosActivations = activationAtEOS.get(mode);
            if (eosActivations != null && !eosActivations.isEmpty()) {
                double sum = 0.0, min = Double.MAX_VALUE, max = 0.0;
                List<Double> values = new ArrayList<>();

                for (Double v : eosActivations.values()) {
                    if (v != null && v > 0) {  // only count non-zero activations
                        sum += v;
                        values.add(v);
                        if (v < min) min = v;
                        if (v > max) max = v;
                    }
                }

                if (!values.isEmpty()) {
                    metrics.activationMin = min;
                    metrics.activationMax = max;

                    // Compute standard deviation
                    double mean = sum / values.size();
                    double variance = 0.0;
                    for (Double v : values) {
                        variance += Math.pow(v - mean, 2);
                    }
                    metrics.activationStdDev = Math.sqrt(variance / values.size());

                    // Compute median
                    java.util.Collections.sort(values);
                    int mid = values.size() / 2;
                    metrics.activationMedian = values.size() % 2 == 0 ?
                        (values.get(mid - 1) + values.get(mid)) / 2.0 :
                        values.get(mid);
                }
                break;  // Only need to compute once (same for all modes)
            }
        }

        // Compute per-entity coherence scores (average of per-entity products)
        computePerEntityCoherenceScores(fdoc, metrics, entityMentionCount, entityParagraphs,
                                        activationAtEOS, activationAtEOP, activationAtEODoc);

        // Compute pairwise entity relatedness metrics (document-level)
        computePairwiseRelatednessMetrics(metrics, pairwiseRelatedness, maxActivationPerEntity);

        // Compute paragraph-level pairwise entity relatedness metrics
        computeParagraphLevelPairwiseRelatedness(metrics, pairwiseRelatedness,
                                                  entitiesPerParagraph, maxActivationPerEntityPerParagraph);

        // Compute derived scores
        metrics.computeTokenCoherence();
        metrics.computeActivationDecay();
        metrics.computeActivationStability();
        metrics.computeCompositeScores();

        return metrics;
    }

    /**
     * Computes per-entity coherence scores by calculating metrics separately for each entity,
     * then aggregating across entities using both mean and median.
     *
     * This approach differs from the aggregate scores which use "product of averages".
     * Per-entity scores use "average of products" which better captures individual entity coherence patterns.
     */
    private void computePerEntityCoherenceScores(
            LinkedDocument fdoc,
            CoherenceMetrics metrics,
            Map<String, Integer> entityMentionCount,
            Map<String, Set<Integer>> entityParagraphs,
            Map<String, Map<Mention, Double>> activationAtEOS,
            Map<String, Map<Mention, Double>> activationAtEOP,
            Map<String, Map<Mention, Double>> activationAtEODoc)
    {
        // Group mentions by entity
        Map<String, List<Mention>> mentionsByEntity = new HashMap<>();
        for (Mention m : fdoc.getMentions()) {
            String entityUri = m.getMentionedConcept();
            mentionsByEntity.computeIfAbsent(entityUri, k -> new ArrayList<>()).add(m);
        }

        // Sort each entity's mentions by token position for distance calculation
        for (List<Mention> mentions : mentionsByEntity.values()) {
            mentions.sort((m1, m2) -> Integer.compare(m1.getTokenOffsetEnd(), m2.getTokenOffsetEnd()));
        }

        // Track which mode to use (just use the first mode available)
        String mode = null;
        for (String m : activationAtEOS.keySet()) {
            mode = m;
            break;
        }
        if (mode == null) return;  // No modes available

        // Calculate per-entity coherence scores
        List<Double> entityCohesionScores = new ArrayList<>();
        List<Double> topicPersistenceScores = new ArrayList<>();
        List<Double> localCoherenceScores = new ArrayList<>();
        List<Double> globalCoherenceScores = new ArrayList<>();

        // Build sentence and paragraph indices for all mentions
        Map<Mention, Integer> mentionSentenceIndex = new HashMap<>();
        Map<Mention, Integer> mentionParagraphIndex = new HashMap<>();
        int sentenceIdx = 0;
        int paragraphIdx = 0;
        for (Paragraph p : fdoc.getParagraphs()) {
            for (Sentence s : p.getSentences()) {
                for (Mention m : s.getMentions()) {
                    mentionSentenceIndex.put(m, sentenceIdx);
                    mentionParagraphIndex.put(m, paragraphIdx);
                }
                sentenceIdx++;
            }
            paragraphIdx++;
        }

        // For each entity, calculate its coherence metrics
        for (Map.Entry<String, List<Mention>> entry : mentionsByEntity.entrySet()) {
            String entityUri = entry.getKey();
            List<Mention> mentions = entry.getValue();

            if (mentions.size() < 1) continue;  // Skip entities with no mentions

            // Calculate per-entity rementions (mentions beyond first)
            int entityRementions = Math.max(0, mentions.size() - 1);

            // Calculate per-entity average sentence distance
            List<Integer> sentenceDistances = new ArrayList<>();
            for (int i = 1; i < mentions.size(); i++) {
                Integer prevSentence = mentionSentenceIndex.get(mentions.get(i-1));
                Integer currSentence = mentionSentenceIndex.get(mentions.get(i));
                if (prevSentence != null && currSentence != null) {
                    sentenceDistances.add(currSentence - prevSentence);
                }
            }
            double avgSentenceDistance = 0.0;
            if (!sentenceDistances.isEmpty()) {
                int sum = 0;
                for (int dist : sentenceDistances) {
                    sum += dist;
                }
                avgSentenceDistance = (double) sum / sentenceDistances.size();
            }

            // Calculate per-entity average paragraph distance
            List<Integer> paragraphDistances = new ArrayList<>();
            for (int i = 1; i < mentions.size(); i++) {
                Integer prevParagraph = mentionParagraphIndex.get(mentions.get(i-1));
                Integer currParagraph = mentionParagraphIndex.get(mentions.get(i));
                if (prevParagraph != null && currParagraph != null) {
                    paragraphDistances.add(currParagraph - prevParagraph);
                }
            }
            double avgParagraphDistance = 0.0;
            if (!paragraphDistances.isEmpty()) {
                int sum = 0;
                for (int dist : paragraphDistances) {
                    sum += dist;
                }
                avgParagraphDistance = (double) sum / paragraphDistances.size();
            }

            // Get per-entity activation values (average across this entity's mentions)
            Map<Mention, Double> eosActivations = activationAtEOS.get(mode);
            Map<Mention, Double> eopActivations = activationAtEOP.get(mode);
            Map<Mention, Double> eodocActivations = activationAtEODoc.get(mode);

            double entityActivationEOS = 0.0;
            double entityActivationEOP = 0.0;
            double entityActivationEODoc = 0.0;
            int activationCount = 0;

            if (eosActivations != null && eopActivations != null && eodocActivations != null) {
                for (Mention m : mentions) {
                    Double eos = eosActivations.get(m);
                    Double eop = eopActivations.get(m);
                    Double eodoc = eodocActivations.get(m);
                    if (eos != null && eop != null && eodoc != null) {
                        entityActivationEOS += eos;
                        entityActivationEOP += eop;
                        entityActivationEODoc += eodoc;
                        activationCount++;
                    }
                }
                if (activationCount > 0) {
                    entityActivationEOS /= activationCount;
                    entityActivationEOP /= activationCount;
                    entityActivationEODoc /= activationCount;
                }
            }

            // Check if entity appears in multiple paragraphs
            Set<Integer> entityParagraphSet = entityParagraphs.get(entityUri);
            boolean persistsAcrossParagraphs = (entityParagraphSet != null && entityParagraphSet.size() > 1);

            // Calculate Entity Cohesion Score for this entity
            // Formula: rementions × (1/(1+avgSentenceDistance)) × max(activationEOS, activationEODoc)
            if (mentions.size() > 1 && avgSentenceDistance >= 0) {
                double rementionFactor = (double) entityRementions;
                double proximityFactor = 1.0 / (1.0 + avgSentenceDistance);
                double activationFactor = Math.max(entityActivationEOS, entityActivationEODoc);
                entityCohesionScores.add(rementionFactor * proximityFactor * activationFactor);
            }

            // Calculate Topic Persistence Score for this entity
            // Formula: (entityPersistence) × (1-decayRate) × coverage
            // For per-entity: use presence across paragraphs as persistence indicator
            if (metrics.paragraphCount > 0 && entityParagraphSet != null) {
                double persistenceFactor = persistsAcrossParagraphs ? 1.0 : 0.0;
                // Decay rate for this entity
                double entityDecayRate = 0.0;
                if (entityActivationEOS > 0) {
                    entityDecayRate = Math.max(0, (entityActivationEOS - entityActivationEODoc) / entityActivationEOS);
                }
                double coverageFactor = (double) mentions.size() / metrics.sentenceCount;
                topicPersistenceScores.add(persistenceFactor * (1.0 - entityDecayRate) * coverageFactor);
            }

            // Calculate Local Coherence Score for this entity
            // Formula: (1/avgSentenceDistance) × mentionsPerSentence × carryForwardFactor
            if (metrics.sentenceCount > 1 && avgSentenceDistance > 0) {
                double proximityFactor = 1.0 / avgSentenceDistance;
                double densityFactor = (double) mentions.size() / metrics.sentenceCount;
                // Carry-forward: check if entity appears in consecutive sentences
                int carryForwardCount = 0;
                for (int i = 1; i < mentions.size(); i++) {
                    Integer prevSent = mentionSentenceIndex.get(mentions.get(i-1));
                    Integer currSent = mentionSentenceIndex.get(mentions.get(i));
                    if (prevSent != null && currSent != null && currSent - prevSent == 1) {
                        carryForwardCount++;
                    }
                }
                double carryForwardFactor = (double) carryForwardCount / metrics.sentenceCount;
                localCoherenceScores.add(proximityFactor * densityFactor * carryForwardFactor);
            }

            // Calculate Global Coherence Score for this entity
            // Formula: activation × paragraphCoverage × persistenceFactor
            if (metrics.paragraphCount > 0 && entityParagraphSet != null) {
                double activation = Math.max(entityActivationEOP, entityActivationEODoc);
                if (activation < 1e-10) activation = entityActivationEOS;  // Fallback
                double coverage = (double) entityParagraphSet.size() / metrics.paragraphCount;
                double persistenceFactor = persistsAcrossParagraphs ? 1.0 : 0.0;
                globalCoherenceScores.add(activation * coverage * persistenceFactor);
            }
        }

        // Compute mean and median for each score type
        metrics.entityCohesionScore_PerEntityMean = computeMean(entityCohesionScores);
        metrics.entityCohesionScore_PerEntityMedian = computeMedian(entityCohesionScores);
        metrics.topicPersistenceScore_PerEntityMean = computeMean(topicPersistenceScores);
        metrics.topicPersistenceScore_PerEntityMedian = computeMedian(topicPersistenceScores);
        metrics.localCoherenceScore_PerEntityMean = computeMean(localCoherenceScores);
        metrics.localCoherenceScore_PerEntityMedian = computeMedian(localCoherenceScores);
        metrics.globalCoherenceScore_PerEntityMean = computeMean(globalCoherenceScores);
        metrics.globalCoherenceScore_PerEntityMedian = computeMedian(globalCoherenceScores);
    }

    /**
     * Computes the mean of a list of doubles.
     */
    private double computeMean(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /**
     * Computes the median of a list of doubles.
     */
    private double computeMedian(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }

    /**
     * Computes pairwise entity relatedness metrics based on cross-activation patterns.
     * Measures how strongly related entities are through the knowledge graph by tracking
     * how much activation flows between entity pairs during spreading activation.
     *
     * @param metrics The metrics object to populate
     * @param pairwiseRelatedness Map of source entity -> target entity -> activation strength
     * @param maxActivationPerEntity Map of entity URI -> maximum activation level reached
     */
    private void computePairwiseRelatednessMetrics(CoherenceMetrics metrics,
                                                    Map<String, Map<String, Double>> pairwiseRelatedness,
                                                    Map<String, Double> maxActivationPerEntity) {
        if (pairwiseRelatedness.isEmpty()) {
            return;
        }

        double totalRelatedness = 0.0;
        double totalWeightedRelatedness = 0.0;
        double maxRelatedness = 0.0;
        int pairCount = 0;

        // Iterate through all entity pairs
        for (Map.Entry<String, Map<String, Double>> sourceEntry : pairwiseRelatedness.entrySet()) {
            String sourceEntity = sourceEntry.getKey();
            double sourceMaxActivation = maxActivationPerEntity.getOrDefault(sourceEntity, 0.0);

            for (Map.Entry<String, Double> targetEntry : sourceEntry.getValue().entrySet()) {
                String targetEntity = targetEntry.getKey();
                double crossActivation = targetEntry.getValue();

                if (crossActivation > 0.0) {
                    pairCount++;
                    totalRelatedness += crossActivation;

                    // Weight by maximum activation of the two entities
                    double targetMaxActivation = maxActivationPerEntity.getOrDefault(targetEntity, 0.0);
                    double maxActivationOfPair = Math.max(sourceMaxActivation, targetMaxActivation);
                    totalWeightedRelatedness += crossActivation * maxActivationOfPair;

                    // Track maximum pairwise relatedness
                    maxRelatedness = Math.max(maxRelatedness, crossActivation);
                }
            }
        }

        // Compute averages
        if (pairCount > 0) {
            metrics.avgPairwiseRelatedness = totalRelatedness / pairCount;
            metrics.avgPairwiseRelatednessWeightedByMaxActivation = totalWeightedRelatedness / pairCount;
            metrics.maxPairwiseRelatedness = maxRelatedness;
            metrics.entityPairCount = pairCount;
        }
    }

    /**
     * Computes paragraph-level pairwise entity relatedness metrics.
     * For each paragraph, filters the document-level pairwise relatedness to only include
     * entity pairs that both appear in that paragraph. Then aggregates across paragraphs
     * weighted by paragraph activation.
     *
     * @param metrics The metrics object to populate
     * @param pairwiseRelatedness Document-level pairwise relatedness data
     * @param entitiesPerParagraph Map of paragraph -> set of entities in that paragraph
     * @param maxActivationPerEntityPerParagraph Map of paragraph -> (entity -> max activation in that paragraph)
     */
    private void computeParagraphLevelPairwiseRelatedness(CoherenceMetrics metrics,
                                                           Map<String, Map<String, Double>> pairwiseRelatedness,
                                                           Map<Integer, Set<String>> entitiesPerParagraph,
                                                           Map<Integer, Map<String, Double>> maxActivationPerEntityPerParagraph) {
        if (entitiesPerParagraph.isEmpty() || pairwiseRelatedness.isEmpty()) {
            return;
        }

        double totalParagraphRelatedness = 0.0;
        double totalWeightedParagraphRelatedness = 0.0;
        int paragraphCount = 0;

        // Iterate through each paragraph
        for (Map.Entry<Integer, Set<String>> paragraphEntry : entitiesPerParagraph.entrySet()) {
            Integer paragraphIndex = paragraphEntry.getKey();
            Set<String> paragraphEntities = paragraphEntry.getValue();

            if (paragraphEntities.size() < 2) {
                // Need at least 2 entities for pairwise relatedness
                continue;
            }

            Map<String, Double> paragraphMaxActivations = maxActivationPerEntityPerParagraph.get(paragraphIndex);

            // Compute average pairwise relatedness for this paragraph
            double paragraphRelatednessSum = 0.0;
            int paragraphPairCount = 0;

            // Find maximum activation in this paragraph (for weighting)
            double maxParagraphActivation = 0.0;
            if (paragraphMaxActivations != null) {
                for (double activation : paragraphMaxActivations.values()) {
                    maxParagraphActivation = Math.max(maxParagraphActivation, activation);
                }
            }

            // Sum up pairwise relatedness for entity pairs both in this paragraph
            double paragraphWeightedSum = 0.0;  // For weighted version

            for (String sourceEntity : paragraphEntities) {
                if (!pairwiseRelatedness.containsKey(sourceEntity)) {
                    continue;
                }

                double sourceMaxActivation = paragraphMaxActivations != null ?
                    paragraphMaxActivations.getOrDefault(sourceEntity, 0.0) : 0.0;

                for (String targetEntity : paragraphEntities) {
                    if (sourceEntity.equals(targetEntity)) {
                        continue;
                    }
                    // Check if there's cross-activation from source to target
                    Double crossActivation = pairwiseRelatedness.get(sourceEntity).get(targetEntity);
                    if (crossActivation != null && crossActivation > 0.0) {
                        paragraphRelatednessSum += crossActivation;

                        // Weight by max activation of the pair (like document-level)
                        double targetMaxActivation = paragraphMaxActivations != null ?
                            paragraphMaxActivations.getOrDefault(targetEntity, 0.0) : 0.0;
                        double maxActivationOfPair = Math.max(sourceMaxActivation, targetMaxActivation);
                        paragraphWeightedSum += crossActivation * maxActivationOfPair;

                        paragraphPairCount++;
                    }
                }
            }

            // Compute averages for this paragraph
            if (paragraphPairCount > 0) {
                double paragraphAvgRelatedness = paragraphRelatednessSum / paragraphPairCount;
                double paragraphWeightedAvg = paragraphWeightedSum / paragraphPairCount;

                totalParagraphRelatedness += paragraphAvgRelatedness;
                totalWeightedParagraphRelatedness += paragraphWeightedAvg;
                paragraphCount++;
            }
        }

        // Compute document-level averages across paragraphs
        if (paragraphCount > 0) {
            metrics.avgPairwiseRelatednessPerParagraph = totalParagraphRelatedness / paragraphCount;
            metrics.avgPairwiseRelatednessPerParagraphWeightedByActivation = totalWeightedParagraphRelatedness / paragraphCount;
        }
    }

    private void aggreagateSpreadingActivationsOncePerEntity(
            LinkedDocument fdoc, Map<String, Mode> modesIndex,
            Map<String, Map<Mention, Double>> activationsAtEncounter,
            Map<String, Map<Mention, Double>> activationAtEOS,
            Map<String, Map<Mention, Double>> activationAtEOP,
            Map<String, Map<Mention, Double>> activationAtEODoc,
            Map<Integer, String> blackboardOnDisk, JedisPool jedisPool)
            throws Exception
    {

        // initializing the result containers;

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationsAtEncounter.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEOS.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEOP.put(m, mentionResults);
        }

        for (String m : modesIndex.keySet())
        {
            Map<Mention, Double> mentionResults = new HashMap<Mention, Double>();
            for (Mention me : fdoc.getMentions())
            {
                mentionResults.put(me, 0.0);
            }
            activationAtEODoc.put(m, mentionResults);
        }

        Map<Long, Map<String, Double>> aggregatedActivation = new HashMap<>();
        Map<String, Map<Long, Map<String, Double>>> saPerEntity = new HashMap<>();

        int prevMentionTokenOffsetEnd = -1;
        int prevMentionSentenceIndex = 0;
        int prevMentionParagraphIndex = 0;
        int currentParagraph = 0;
        int currentSentence = 0;
        for (Paragraph p : fdoc.getParagraphs())
        {
            for (Sentence s : p.getSentences())
            {
                List<Mention> mentions = s.getOrderedMentions();

                // activation at encounter
                for (int i = 0; i < mentions.size(); i++)
                { // iterate through all mentions to compute the activation at
                  // encounter
                    Mention mention = mentions.get(i);
                    Label res = DynamicLabel.label("Resource");
                    Node seed = this.db.findNode(res, "uri",
                            mention.getMentionedConcept());
                    if (prevMentionTokenOffsetEnd > -1)
                    { // if we are not on the first mention, first apply reading
                      // decay to all the existing activations in the memory.
                        int deltaToken = mentions.get(i).getTokenOffsetEnd()
                                - prevMentionTokenOffsetEnd; // distanta o punem
                                                             // sa se calculeze
                                                             // de cand se
                                                             // termina
                                                             // mentiunea,
                        // in ideea ca entitatea devine activa in cap dupa ce a
                        // fost citita tot numele.
                        // In felul asta, entitatile mai lungi devin natural mai
                        // putin active in memorie.
                        System.out.println("delta token: " + deltaToken);
                        int deltaSentence = currentSentence
                                - prevMentionSentenceIndex;
                        System.out.println("delta sentence: " + deltaSentence);
                        int deltaParagraph = currentParagraph
                                - prevMentionParagraphIndex;
                        System.out
                                .println("delta paragraph: " + deltaParagraph);

                        System.out.println(fdoc.getPath()
                                + " : applying reading decay...");
                        for (Long n : aggregatedActivation.keySet())
                        {
                            Map<String, Double> modeActivations = aggregatedActivation
                                    .get(n);
                            for (String mode : modeActivations.keySet())
                                modeActivations
                                        .put(mode,
                                                modeActivations.get(mode)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingTokenDecay(),
                                                                deltaToken)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingSentenceDecay(),
                                                                deltaSentence)
                                                        * Math.pow(
                                                                modesIndex
                                                                        .get(mode)
                                                                        .getReadingParagraphDecay(),
                                                                deltaParagraph));
                            aggregatedActivation.put(n, modeActivations);
                        }
                        System.out.println(fdoc.getPath()
                                + " : reading decay applied");

                        if (aggregatedActivation.containsKey(seed.getId()))
                        {
                            Map<String, Double> activationRes = aggregatedActivation
                                    .get(seed.getId());
                            for (Entry<String, Double> e : activationRes
                                    .entrySet())
                            {
                                if (activationsAtEncounter.containsKey(e
                                        .getKey())
                                        && activationRes.get(e.getKey()) != null)
                                    activationsAtEncounter.get(e.getKey()).put(
                                            mention, e.getValue());
                                else
                                {
                                    System.out
                                            .println("activations at encounter does not contain mode : "
                                                    + e.getKey());
                                    System.out
                                            .println("activations at encounter size : "
                                                    + activationsAtEncounter
                                                            .size());
                                    System.out.println("activationRes size : "
                                            + activationRes.size());
                                }
                            }
                        }
                    }
                    // update the indexes to the current ones
                    System.out.println("prev token :"
                            + prevMentionTokenOffsetEnd + " . current token : "
                            + mentions.get(i).getTokenOffsetEnd());
                    prevMentionTokenOffsetEnd = mentions.get(i)
                            .getTokenOffsetEnd();
                    System.out.println("prev sentence :"
                            + prevMentionSentenceIndex
                            + " . current sentence : " + currentSentence);
                    prevMentionSentenceIndex = currentSentence;
                    System.out.println("prev paragraph :"
                            + prevMentionParagraphIndex
                            + " . current paragraph : " + currentParagraph);
                    prevMentionParagraphIndex = currentParagraph;

                    if (i < mentions.size() - 1)
                    { // if we are not on the last item
                        System.out
                                .println(fdoc.getPath()
                                        + " : Aggregating the activations from mention: "
                                        + i);
                        Map<Long, Map<String, Double>> tidalActivation;
                        if (saPerEntity.containsKey(mention
                                .getMentionedConcept()))
                            tidalActivation = saPerEntity.get(mention
                                    .getMentionedConcept());
                        else
                        {
                            boolean existsButNotReadable = false;
                            if (blackboardOnDisk.containsKey(mention
                                    .getMentionedConcept().hashCode()))
                            {
                                try
                                {
                                    System.out
                                            .println(fdoc.getPath()
                                                    + " reading activations from disk for "
                                                    + mention
                                                            .getMentionedConcept());
                                    saPerEntity
                                            .put(mention.getMentionedConcept(),
                                                    this.readActivationFromFile(blackboardOnDisk
                                                            .get(mention
                                                                    .getMentionedConcept()
                                                                    .hashCode())));
                                } catch (Exception ex)
                                {
                                    existsButNotReadable = true;
                                }

                                if (existsButNotReadable)
                                {
                                    System.out
                                            .println(fdoc.getPath()
                                                    + " :exists but nor readable so SA from :"
                                                    + mention
                                                            .getMentionedConcept());
                                    Map<Long, Map<String, Double>> activations = this
                                            .runSpreadingActivation(fdoc, seed,
                                                    modesIndex);
                                    System.out.println(fdoc.getPath()
                                            + " : FINISHED SA from :"
                                            + mention.getMentionedConcept());
                                    saPerEntity.put(
                                            mention.getMentionedConcept(),
                                            activations);
                                    System.out.println(fdoc.getPath()
                                            + " : writing activations from "
                                            + mention.getMentionedConcept()
                                            + "  to disk...");
                                    File f = new File(
                                            "results/spreading_Activation/temp",
                                            mention.getMentionedConcept()
                                                    .hashCode() + ".tmp");
                                    if (!f.exists())
                                    {
                                        FileOutputStream fos = new FileOutputStream(
                                                new File(
                                                        "results/spreading_Activation/temp",
                                                        mention.getMentionedConcept()
                                                                .hashCode()
                                                                + ".tmp"));
                                        ObjectOutputStream oos = new ObjectOutputStream(
                                                fos);
                                        oos.writeObject(activations);
                                        oos.close();
                                        blackboardOnDisk
                                                .put(mention
                                                        .getMentionedConcept()
                                                        .hashCode(),
                                                        "results/spreading_Activation/temp/"
                                                                + mention
                                                                        .getMentionedConcept()
                                                                        .hashCode()
                                                                + ".tmp");
                                    }
                                }
                            } else
                            {
                                System.out
                                        .println(fdoc.getPath()
                                                + "NOT IN CACHE AND NOT ON DISK AT AGGREGATION TIME!! : SA from :"
                                                + mention.getMentionedConcept());
                                Map<Long, Map<String, Double>> activations = this
                                        .runSpreadingActivation(fdoc, seed,
                                                modesIndex);
                                System.out.println(fdoc.getPath()
                                        + " : FINISHED SA from :"
                                        + mention.getMentionedConcept());
                                saPerEntity.put(mention.getMentionedConcept(),
                                        activations);
                                System.out.println(fdoc.getPath()
                                        + " : writing activations from "
                                        + mention.getMentionedConcept()
                                        + "  to disk...");
                                File f = new File(
                                        "results/spreading_Activation/temp",
                                        mention.getMentionedConcept()
                                                .hashCode() + ".tmp");
                                if (!f.exists())
                                {
                                    FileOutputStream fos = new FileOutputStream(
                                            new File(
                                                    "results/spreading_Activation/temp",
                                                    mention.getMentionedConcept()
                                                            .hashCode()
                                                            + ".tmp"));
                                    ObjectOutputStream oos = new ObjectOutputStream(
                                            fos);
                                    oos.writeObject(activations);
                                    oos.close();
                                    blackboardOnDisk
                                            .put(mention.getMentionedConcept()
                                                    .hashCode(),
                                                    "results/spreading_Activation/temp/"
                                                            + mention
                                                                    .getMentionedConcept()
                                                                    .hashCode()
                                                            + ".tmp");
                                }
                            }

                            tidalActivation = saPerEntity.get(mention
                                    .getMentionedConcept());

                        }

                        for (Long tidalNode : tidalActivation.keySet())
                        {
                            Map<String, Double> mAggrAct;
                            if (aggregatedActivation.containsKey(tidalNode))
                            {
                                mAggrAct = aggregatedActivation.get(tidalNode);
                            } else
                            {
                                mAggrAct = new HashMap<String, Double>();
                            }

                            for (String mode : tidalActivation.get(tidalNode)
                                    .keySet())
                            {
                                if (mAggrAct.containsKey(mode))
                                {
                                    double newAggrActivation = mAggrAct
                                            .get(mode)
                                            + tidalActivation.get(tidalNode)
                                                    .get(mode);
                                    if (newAggrActivation > 1.0)
                                        newAggrActivation = 1.0;
                                    mAggrAct.put(mode, newAggrActivation);
                                } else
                                    mAggrAct.put(
                                            mode,
                                            tidalActivation.get(tidalNode).get(
                                                    mode));
                            }
                            aggregatedActivation.put(tidalNode, mAggrAct);
                        }
                    }

                } // all aggregated activations of current sentence have been
                  // updated.

                // iterate through mentions to compute activation at end of
                // sentence.
                //System.out.println("Applying aggregations at end of sentence. ");
                for (Mention m : s.getMentions())
                {
                    Label res = DynamicLabel.label("Resource");
                    Node seed = this.db.findNode(res, "uri",
                            m.getMentionedConcept());
                    if (aggregatedActivation.containsKey(seed.getId()))
                    {
                        Map<String, Double> activationRes = aggregatedActivation
                                .get(seed.getId());
                        for (Entry<String, Double> e : activationRes.entrySet())
                        {
                            activationAtEOS.get(e.getKey())
                                    .put(m, e.getValue());
                        }
                    }
                }
                currentSentence++;
            }

            // iterate through mentions to compute activation at end of
            // paragraph.
            //System.out.println("Applying aggregations at end of paragraph. ");
            for (Mention m : p.getMentions())
            {
                Label res = DynamicLabel.label("Resource");
                Node seed = this.db.findNode(res, "uri",
                        m.getMentionedConcept());
                if (aggregatedActivation.containsKey(seed.getId()))
                {
                    Map<String, Double> activationRes = aggregatedActivation
                            .get(seed.getId());
                    for (Entry<String, Double> e : activationRes.entrySet())
                    {
                        activationAtEOP.get(e.getKey()).put(m, e.getValue());
                    }
                }
            }
            currentParagraph++;
        }

        // iterate through mentions to compute activation at end of document.
        //System.out.println("Applying aggregations at end of document. ");
        for (Mention m : fdoc.getMentions())
        {
            Label res = DynamicLabel.label("Resource");
            Node seed = this.db.findNode(res, "uri", m.getMentionedConcept());
            if (aggregatedActivation.containsKey(seed.getId()))
            {
                Map<String, Double> activationRes = aggregatedActivation
                        .get(seed.getId());
                for (Entry<String, Double> e : activationRes.entrySet())
                {
                    activationAtEODoc.get(e.getKey()).put(m, e.getValue());
                }
            }
        }

    }

    public void readWithSpreadingActivationOncePerEntityJedis(
            LinkedDocument fdoc, Map<String, Mode> modesIndex, Jedis jedis)
    {

        // now run SA once for each entity

        Set<String> allEntities = new HashSet<>();
        for (Mention m : fdoc.getMentions())
        {
            allEntities.add(m.getMentionedConcept());
        }

        for (String uri : allEntities)
        {
            try
            {
                Label res = DynamicLabel.label("Resource");
                Node seed = this.db.findNode(res, "uri", uri);

                if (jedis.sismember("seeds", String.valueOf(seed.getId())))
                {
                    System.out.println(fdoc.getPath() + " : found " + uri
                            + " in Redis. moving on");
                    /*
                     * fetching from disk is postponed to when we really need
                     * it, at the aggregation step. try { saPerEntity.put(uri,
                     * this
                     * .readActivationFromFile(blackboardOnDisk.get(uri.hashCode
                     * ()))); } catch(Exception ex) { existsButNotReadable =
                     * true; }
                     */
                } else
                {
                    System.out.println(fdoc.getPath() + " : SA from :" + uri);
                    Map<Long, Map<String, Double>> activations = this
                            .runSpreadingActivation(fdoc, seed, modesIndex);
                    System.out.println(fdoc.getPath() + " : FINISHED SA from :"
                            + uri);
                    // saPerEntity.put(uri, activations);
                    /*
                     * System.out.println(fdoc.getLdoc().docPath
                     * +" : writing activations from " + uri + "  to disk...");
                     * File f = new File ("results/spreading_Activation/temp",
                     * uri.hashCode()+".tmp"); if (!f.exists()) {
                     * FileOutputStream fos = new FileOutputStream(new File
                     * ("results/spreading_Activation/temp",
                     * uri.hashCode()+".tmp")); ObjectOutputStream oos = new
                     * ObjectOutputStream(fos); oos.writeObject(activations);
                     * oos.close(); blackboardOnDisk.put(uri.hashCode(),
                     * "results/spreading_Activation/temp/" +
                     * uri.hashCode()+".tmp"); }
                     */
                    System.out.println(fdoc.getPath()
                            + " : writing activations from " + uri
                            + "  to Redis...");
                    Pipeline p = jedis.pipelined();

                    for (Entry<Long, Map<String, Double>> ea : activations
                            .entrySet())
                    {
                        p.sadd(String.valueOf(seed.getId()),
                                String.valueOf(ea.getKey()));
                        for (Entry<String, Double> ev : ea.getValue()
                                .entrySet())
                        {

                            p.hset(seed.getId() + "->" + ea.getKey(),
                                    modesIndex.get(ev.getKey()).graphSetting
                                            .getSettingName(), String
                                            .valueOf(ev.getValue()));
                            // jedis.hset(seed.getId()+"->" +ea.getKey(),
                            // ev.getKey(), String.valueOf(ev.getValue()));

                        }
                    }
                    System.out.println(fdoc.getPath() + " : WRITTEN " + uri
                            + "  to Redis");
                    p.sadd("seeds", String.valueOf(seed.getId()));
                    p.sync();
                }
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }

    }

    public void readWithSpreadingActivationOncePerEntity(LinkedDocument fdoc,
            Map<String, Mode> modesIndex,
            Map<Integer, String> blackboardOnDisk, JedisPool jedisPool)
            throws Exception
    {

        // now run SA once for each entity

        Set<String> allEntities = new HashSet<>();
        for (Mention m : fdoc.getMentions())
        {
            allEntities.add(m.getMentionedConcept());
        }

        for (String uri : allEntities)
        {

            Label res = DynamicLabel.label("Resource");
            Node seed = this.db.findNode(res, "uri", uri);
            /*
             * if (tidalActivationBlackboard.containsKey(uri.hashCode())) {
             * System.out.println(fdoc.getLdoc().docPath +" :  found " + uri +
             * " in memory. fetching it"); saPerEntity.put(uri,
             * tidalActivationBlackboard.get(uri)); } else
             */
            // boolean existsButNotReadable = false;
            if (blackboardOnDisk.containsKey(uri.hashCode()))
            {
                System.out.println(fdoc.getPath() + " : found " + uri
                        + " on disk. moving on");
                /*
                 * fetching from disk is postponed to when we really need it, at
                 * the aggregation step. try { saPerEntity.put(uri,
                 * this.readActivationFromFile
                 * (blackboardOnDisk.get(uri.hashCode()))); } catch(Exception
                 * ex) { existsButNotReadable = true; }
                 */
            } else if (!blackboardOnDisk.containsKey(uri.hashCode()))
            {// || existsButNotReadable) {
                System.out.println(fdoc.getPath() + " : SA from :" + uri);
                Map<Long, Map<String, Double>> activations = this
                        .runSpreadingActivation(fdoc, seed, modesIndex);
                System.out.println(fdoc.getPath() + " : FINISHED SA from :"
                        + uri);
                // saPerEntity.put(uri, activations);
                System.out
                        .println(fdoc.getPath()
                                + " : writing activations from " + uri
                                + "  to disk...");
                File f = new File("results/spreading_Activation/temp",
                        uri.hashCode() + ".tmp");
                if (!f.exists())
                {
                    FileOutputStream fos = new FileOutputStream(new File(
                            "results/spreading_Activation/temp", uri.hashCode()
                                    + ".tmp"));
                    ObjectOutputStream oos = new ObjectOutputStream(fos);
                    oos.writeObject(activations);
                    oos.close();
                    blackboardOnDisk.put(
                            uri.hashCode(),
                            "results/spreading_Activation/temp/"
                                    + uri.hashCode() + ".tmp");
                }

            }
        }

    }

    private boolean checkResourceIsEquivalentToCateg(String uri1, String uri2)
    {
        if (!uri1.contains("Category:") && uri2.contains("Category:"))
        {
            String name1 = getResourceName(uri1);
            String name2 = getCategoryName(uri2);
            if (this.levenshteinDistance(name1, name2) <= 1)
                return true;
        } else if (uri1.contains("Category:") && !uri2.contains("Category:"))
        {
            String name1 = getCategoryName(uri1);
            String name2 = getResourceName(uri2);
            if (this.levenshteinDistance(name1, name2) <= 1)
                return true;
        }

        return false;
    }

    public String getResourceName(String uri)
    {
        return uri.replaceAll("http://dbpedia.org/resource/", "");

    }

    public String getCategoryName(String uri)
    {
        return uri.replaceAll("http://dbpedia.org/resource/Category:", "");

    }

    public int levenshteinDistance(CharSequence lhs, CharSequence rhs)
    {
        int len0 = lhs.length() + 1;
        int len1 = rhs.length() + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++)
            cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++)
        {
            // initial cost of skipping prefix in String s1
            newcost[0] = j;

            // transformation cost for each letter in s0
            for (int i = 1; i < len0; i++)
            {
                // matching current letters in both strings
                int match = (lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete),
                        cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }

    private void updateTidalActivation(
            Map<Long, Map<String, Double>> tidalActivation,
            Map<Long, Set<String>> fired, Map<Long, Set<String>> burned,
            Map<String, Mode> modesIndex, boolean first)
    {

        for (Entry<Long, Set<String>> en : fired.entrySet())
        {
            Long nodeid = en.getKey();
            Set<String> firedModes = fired.get(nodeid);

            Node f = this.db.getNodeById(nodeid);
            int nbRels = IteratorUtil.count(f.getRelationships());

            double exclNorm = 0.0;
            double impNorm = 0.0;

            Map<Long, Map<String, Double>> temporaryTidalActivation = new HashMap<>();
            for (Relationship r : f.getRelationships())
            {
                Node neighbour = r.getOtherNode(f);
                if (neighbour.hasProperty("uri"))
                {
                    double exclusivity = 0.0;
                    if (checkResourceIsEquivalentToCateg(f.getProperty("uri")
                            .toString(), neighbour.getProperty("uri")
                            .toString()))
                        exclusivity = 1.0;
                    else if (r.hasProperty("Exclusivity"))
                        exclusivity = Double.valueOf(r.getProperty(
                                "Exclusivity").toString());

                    double importance = log2(neighbour.getDegree() + 1)
                            / DEGREE_LOG_NORMALIZATION;

                    exclNorm = exclNorm + exclusivity;
                    impNorm = impNorm + importance;

                    Map<String, Double> modeTidalActivations;
                    if (temporaryTidalActivation.containsKey(neighbour.getId()))
                    {
                        modeTidalActivations = temporaryTidalActivation
                                .get(neighbour.getId());
                    } else
                        modeTidalActivations = new HashMap<>();

                    for (String m : firedModes)
                    {// for each mode:
                        if (!modeTidalActivations.containsKey(m))
                            modeTidalActivations.put(m, 0.0);

                        double newTidalActivation = 0.0;

                        // computation of new tidal activation starts
                        newTidalActivation = tidalActivation.get(nodeid).get(m); // first
                                                                                 // multiply
                                                                                 // the
                                                                                 // entire
                                                                                 // activation
                                                                                 // of
                                                                                 // the
                                                                                 // source
                        if (modesIndex.get(m).usesExclusivity())
                            newTidalActivation = newTidalActivation
                                    * exclusivity;
                        if (modesIndex.get(m).usesImportance())
                            newTidalActivation = newTidalActivation
                                    * importance;
                        if (!modesIndex.get(m).usesExclusivity()
                                && !modesIndex.get(m).usesImportance())
                            newTidalActivation = newTidalActivation
                                    / ((1.0) * nbRels);
                        if (!first)
                            newTidalActivation = newTidalActivation
                                    * modesIndex.get(m).getGraphDecay();

                        modeTidalActivations.put(m, modeTidalActivations.get(m)
                                + newTidalActivation);
                    }
                    temporaryTidalActivation.put(neighbour.getId(),
                            modeTidalActivations);
                }
            }
            for (Relationship r : f.getRelationships())
            {
                Node neighbour = r.getOtherNode(f);
                if (neighbour.hasProperty("uri"))
                {
                    for (String m : firedModes)
                    {
                        if (modesIndex.get(m).usesExclusivity()
                                && !modesIndex.get(m).usesImportance())
                            temporaryTidalActivation.get(neighbour.getId())
                                    .put(m,
                                            temporaryTidalActivation.get(
                                                    neighbour.getId()).get(m)
                                                    / exclNorm);
                        if (modesIndex.get(m).usesImportance()
                                && !modesIndex.get(m).usesExclusivity())
                            temporaryTidalActivation.get(neighbour.getId())
                                    .put(m,
                                            temporaryTidalActivation.get(
                                                    neighbour.getId()).get(m)
                                                    / impNorm);
                        if (modesIndex.get(m).usesExclusivity()
                                && modesIndex.get(m).usesImportance())
                            temporaryTidalActivation.get(neighbour.getId())
                                    .put(m,
                                            temporaryTidalActivation.get(
                                                    neighbour.getId()).get(m)
                                                    / (exclNorm * impNorm));

                    }
                }
            }

            for (Entry<Long, Map<String, Double>> tempe : temporaryTidalActivation
                    .entrySet())
            {
                Map<String, Double> currentTidalAct;
                if (tidalActivation.containsKey(tempe.getKey()))
                {
                    currentTidalAct = tidalActivation.get(tempe.getKey());
                } else
                {
                    currentTidalAct = new HashMap<>();
                }

                for (Entry<String, Double> modee : tempe.getValue().entrySet())
                {
                    double val = 0.0;
                    if (currentTidalAct.containsKey(modee.getKey()))
                    {
                        val = currentTidalAct.get(modee.getKey())
                                + modee.getValue();
                    } else
                        val = modee.getValue();
                    if (val > 1.0)
                        val = 1.0;

                    currentTidalAct.put(modee.getKey(), val);
                }
                tidalActivation.put(tempe.getKey(), currentTidalAct);
            }

        }
    }
}
