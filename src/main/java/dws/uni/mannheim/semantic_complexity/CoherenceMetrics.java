package dws.uni.mannheim.semantic_complexity;

/**
 * Container for text coherence metrics computed during spreading activation.
 * All metrics are derived from knowledge graph processing and entity mention patterns.
 */
public class CoherenceMetrics {

    // GRAPH CONNECTIVITY METRICS
    public int activatedNodeCount = 0;           // Total unique KG nodes activated
    public int uniqueEntityCount = 0;            // Distinct entities mentioned
    public int totalMentionCount = 0;            // Total mentions (with repetitions)
    public double entityRepetitionRatio = 0.0;   // totalMentions / uniqueEntities
    public double avgNodeDegree = 0.0;           // Average connectivity of nodes
    public int relationshipsTraversed = 0;       // Total graph edges used
    public double avgExclusivityScore = 0.0;     // Average relationship strength
    public int firingRoundsCount = 0;            // Propagation depth

    // ACTIVATION DYNAMICS - Measures semantic processing at key points
    public double activationAtEncounter = 0.0;   // When entity first appears
    public double activationAtEOS = 0.0;         // End of sentence
    public double activationAtEOP = 0.0;         // End of paragraph
    public double activationAtEODoc = 0.0;       // End of document
    public double activationDecayRate = 0.0;     // How fast concepts fade
    public double activationStability = 0.0;     // Consistency across stages

    // ACTIVATION STATISTICS - Distribution measures
    public double activationStdDev = 0.0;        // Standard deviation
    public double activationMin = 0.0;           // Minimum activation
    public double activationMax = 0.0;           // Maximum activation
    public double activationMedian = 0.0;        // Median activation

    // DISTANCE METRICS - Coherence through proximity
    public double avgTokenDistance = 0.0;        // Tokens between mentions (mean)
    public double stdTokenDistance = 0.0;        // Standard deviation of token distances
    public double maxTokenDistance = 0.0;        // Largest token gap
    public double avgSentenceDistance = 0.0;     // Sentences between mentions (mean)
    public double stdSentenceDistance = 0.0;     // Standard deviation of sentence distances
    public double maxSentenceDistance = 0.0;     // Largest sentence gap
    public double avgParagraphDistance = 0.0;    // Paragraphs between mentions (mean)
    public double stdParagraphDistance = 0.0;    // Standard deviation of paragraph distances
    public double maxParagraphDistance = 0.0;    // Largest paragraph gap
    public double tokenCoherenceScore = 0.0;     // 1/(1+avgTokenDistance)

    // TEXT STRUCTURE METRICS
    public int sentenceCount = 0;
    public int paragraphCount = 0;
    public int tokenCount = 0;
    public double avgSentenceLength = 0.0;       // tokens / sentences
    public double avgParagraphLength = 0.0;      // sentences / paragraphs
    public double entityDensity = 0.0;           // mentions / tokens

    // ENTITY DISTRIBUTION METRICS
    public double entitiesPerSentence = 0.0;
    public double entitiesPerParagraph = 0.0;
    public int sentencesWithEntities = 0;
    public int paragraphsWithEntities = 0;
    public double sentenceCoverageRatio = 0.0;   // % sentences with entities
    public double paragraphCoverageRatio = 0.0;  // % paragraphs with entities

    // CONCEPT REACTIVATION - Core coherence indicator
    public int rementionCount = 0;               // Total re-mentions
    public double avgRementionsPerEntity = 0.0;  // Average entity reuse
    public int entityPersistence = 0;            // Entities in multiple paragraphs
    public int carriedForwardEntities = 0;       // Concepts crossing boundaries

    // COMPOSITE COHERENCE SCORES (Aggregate-level: product of averages)
    public double entityCohesionScore = 0.0;     // Remention * proximity * activation
    public double semanticConnectivityScore = 0.0; // Graph structure quality
    public double topicPersistenceScore = 0.0;   // Concept maintenance
    public double localCoherenceScore = 0.0;     // Sentence-to-sentence
    public double globalCoherenceScore = 0.0;    // Document-wide

    // PAIRWISE ENTITY RELATEDNESS - How strongly connected are entities in the knowledge graph
    public double avgPairwiseRelatedness = 0.0;  // Mean cross-activation between entity pairs (document-level)
    public double avgPairwiseRelatednessWeightedByMaxActivation = 0.0; // Weighted by max activation of pair
    public double maxPairwiseRelatedness = 0.0;  // Strongest entity-entity connection
    public int entityPairCount = 0;              // Number of entity pairs analyzed

    // PARAGRAPH-LEVEL PAIRWISE RELATEDNESS - Local coherence through entity relatedness
    public double avgPairwiseRelatednessPerParagraph = 0.0;  // Mean pairwise relatedness within paragraphs
    public double avgPairwiseRelatednessPerParagraphWeightedByActivation = 0.0; // Weighted by paragraph activation

    // PER-ENTITY COHERENCE SCORES (Entity-level: average/median of per-entity products)
    public double entityCohesionScore_PerEntityMean = 0.0;    // Mean of per-entity cohesion
    public double entityCohesionScore_PerEntityMedian = 0.0;  // Median of per-entity cohesion
    public double topicPersistenceScore_PerEntityMean = 0.0;  // Mean of per-entity persistence
    public double topicPersistenceScore_PerEntityMedian = 0.0; // Median of per-entity persistence
    public double localCoherenceScore_PerEntityMean = 0.0;    // Mean of per-entity local coherence
    public double localCoherenceScore_PerEntityMedian = 0.0;  // Median of per-entity local coherence
    public double globalCoherenceScore_PerEntityMean = 0.0;   // Mean of per-entity global coherence
    public double globalCoherenceScore_PerEntityMedian = 0.0; // Median of per-entity global coherence

    /**
     * Computes composite coherence scores from basic metrics.
     * Call this after all basic metrics are populated.
     */
    public void computeCompositeScores() {
        // Entity Cohesion: remention * proximity * activation
        // Use activationAtEOS instead of EODoc since EODoc can decay to near-zero
        if (uniqueEntityCount > 0 && avgSentenceDistance > 0) {
            entityCohesionScore = (rementionCount / (double) uniqueEntityCount) *
                                  (1.0 / (1.0 + avgSentenceDistance)) *
                                  Math.max(activationAtEOS, activationAtEODoc);
        }

        // Semantic Connectivity: graph structure quality
        // Fallback to simpler ratio if exclusivity/relationships not tracked
        if (uniqueEntityCount > 0 && activatedNodeCount > 0) {
            if (avgExclusivityScore > 0 && relationshipsTraversed > 0) {
                semanticConnectivityScore = (activatedNodeCount / (double) uniqueEntityCount) *
                                            avgExclusivityScore *
                                            (relationshipsTraversed / (double) activatedNodeCount);
            } else {
                // Fallback: just use activation spread ratio
                semanticConnectivityScore = (activatedNodeCount / (double) uniqueEntityCount);
            }
        }

        // Topic Persistence: concept maintenance across document
        // Use EOS activation instead of decay rate to avoid zeros from extreme EODoc decay
        if (uniqueEntityCount > 0) {
            // Use activation level as indicator of topic strength rather than decay rate
            double topicStrength = Math.max(activationAtEOS, activationAtEOP);
            if (topicStrength < 1e-10) topicStrength = (1.0 - activationDecayRate);  // Fallback to decay-based
            topicPersistenceScore = (entityPersistence / (double) uniqueEntityCount) *
                                    topicStrength *
                                    sentenceCoverageRatio;
        }

        // Local Coherence: sentence-to-sentence connectivity
        if (sentenceCount > 1 && avgSentenceDistance > 0) {
            localCoherenceScore = (1.0 / avgSentenceDistance) *
                                  entitiesPerSentence *
                                  (carriedForwardEntities / (double) sentenceCount);
        }

        // Global Coherence: document-wide semantic unity
        // Use activationAtEOP instead of EODoc since EODoc can decay to near-zero
        if (paragraphCount > 0) {
            double activation = Math.max(activationAtEOP, activationAtEODoc);
            if (activation < 1e-10) activation = activationAtEOS;  // Fallback to EOS
            globalCoherenceScore = activation *
                                   paragraphCoverageRatio *
                                   (entityPersistence / (double) paragraphCount);
        }
    }

    /**
     * Computes activation decay rate from encounter to document end.
     */
    public void computeActivationDecay() {
        if (activationAtEncounter > 0) {
            activationDecayRate = (activationAtEncounter - activationAtEODoc) / activationAtEncounter;
        }
    }

    /**
     * Computes activation stability across stages.
     * Higher value = more consistent activation (better coherence).
     */
    public void computeActivationStability() {
        double[] stages = {activationAtEncounter, activationAtEOS, activationAtEOP, activationAtEODoc};
        double mean = (activationAtEncounter + activationAtEOS + activationAtEOP + activationAtEODoc) / 4.0;

        if (mean > 0) {
            double variance = 0.0;
            for (double stage : stages) {
                variance += Math.pow(stage - mean, 2);
            }
            variance /= 4.0;
            double stdDev = Math.sqrt(variance);

            // Stability is inverse of coefficient of variation (normalized)
            activationStability = 1.0 - (stdDev / mean);
            if (activationStability < 0) activationStability = 0;
        }
    }

    /**
     * Computes token coherence score: 1/(1+avgDistance).
     * Higher value = shorter distances = better coherence.
     */
    public void computeTokenCoherence() {
        tokenCoherenceScore = 1.0 / (1.0 + avgTokenDistance);
    }

    /**
     * Default constructor
     */
    public CoherenceMetrics() {
        // All fields initialized to 0 or 0.0
    }
}
