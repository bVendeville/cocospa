# Text Coherence Features - Complete Implementation Guide

This document describes all the features extracted from the knowledge graph spreading activation algorithm for measuring text coherence.

## Overview

The CoCo API uses spreading activation over DBpedia knowledge graph to compute text complexity. The same algorithm captures rich information about semantic coherence - how well concepts in a text connect and relate to each other.

## Feature Categories

### 1. GRAPH CONNECTIVITY METRICS
Track how concepts connect through the knowledge graph

- `activatedNodeCount` ✓ - Total unique KG nodes activated
- `uniqueEntityCount` - Distinct entities mentioned
- `totalMentionCount` - Total mentions (with repetitions)
- `entityRepetitionRatio` - Repetition indicates coherence
- `avgNodeDegree` - Average connectivity
- `relationshipsTraversedCount` - Total graph edges used
- `avgExclusivityScore` - Semantic relationship strength
- `firingRoundsCount` - Propagation depth

### 2. ACTIVATION DYNAMICS
How activation changes as text is processed

**At Four Key Points:**
- `activationAtEncounter` - When entity first appears
- `activationAtEOS` - End of sentence
- `activationAtEOP` - End of paragraph
- `activationAtEODoc` - End of document

**Statistics for Each:**
- Mean, median, std dev, min, max, variance
- Percentiles (25th, 75th)
- Activation decay rate
- Stability score

### 3. READING-BASED COHERENCE
Distance between entity mentions

**Token Level:**
- `avgTokenDistance` - Tokens between mentions
- `maxTokenDistance` - Largest gap
- `tokenCoherenceScore` - 1/(1+avgDistance)

**Sentence Level:**
- `avgSentenceDistance` - Sentences between rementions
- `entitiesPerSentence` - Mention density
- `sentenceCoverageRatio` - % of sentences with entities

**Paragraph Level:**
- `avgParagraphDistance` - Paragraphs between rementions
- `entitiesPerParagraph` - Paragraph-level density
- `carriedForwardEntities` - Concepts crossing boundaries

### 4. TEXT STRUCTURE
Basic document metrics

- `sentenceCount`, `paragraphCount`, `tokenCount`
- `avgSentenceLength`, `avgParagraphLength`
- `entityDensity` - mentions / tokens
- `entitySpan` - Text coverage

### 5. SEMANTIC COHERENCE
High-level coherence indicators

**Concept Reactivation:**
- `rementionCount` - Total re-mentions
- `avgRementionsPerEntity` - Average reuse
- `entityPersistence` - Multi-paragraph presence

**Composite Scores:**
- `entityCohesionScore` - Remention * proximity * activation
- `semanticConnectivityScore` - Graph structure quality
- `topicPersistenceScore` - Concept maintenance
- `localCoherenceScore` - Sentence-to-sentence
- `globalCoherenceScore` - Document-wide

## Coherence Interpretation

### High Coherence Signals
✓ Entities mentioned multiple times
✓ Short distances between mentions
✓ High activation at document end
✓ Entities distributed throughout text
✓ Strong semantic relationships (high exclusivity)
✓ Low activation decay

### Low Coherence Signals
✗ One-time entity mentions
✗ Large gaps between mentions
✗ Rapid activation decay
✗ Scattered entity distribution
✗ Weak semantic connections
✗ Low final activation

## Implementation Phases

### Phase 1: Core Metrics (Tier 1) ⭐ START HERE
Focus on most important coherence indicators:

1. All activation stages (Encounter, EOS, EOP, EODoc)
2. Basic counts (sentences, paragraphs, entities, mentions)
3. Distance metrics (token, sentence, paragraph)
4. Remention tracking
5. Coverage ratios

**Files to modify:**
- `SAComplexityModes.java` - Track additional metrics during algorithm
- `TextComplexityAssesment.java` - Create CoherenceMetrics return object
- `TextComplexityAssesmentResponseObject.java` - Add new fields
- `TextComplexityAssesmentController.java` - Pass metrics to response

### Phase 2: Graph Structure (Tier 2)
6. Node degree statistics (avg, max, min)
7. Relationship metrics (count, exclusivity)
8. Firing/burning metrics
9. Propagation depth

### Phase 3: Advanced Analytics (Tier 3)
10. Full activation distribution statistics
11. Semantic clustering
12. Topic persistence analysis
13. Composite coherence scores

## Example Use Cases

### Academic Writing Analysis
High coherence expected:
- Entities reintroduced regularly
- Concepts build on each other
- Clear topic progression

### News Articles
Moderate coherence:
- Main entities mentioned throughout
- Supporting entities mentioned once
- Inverted pyramid structure

### Stream of Consciousness
Low coherence:
- Entities mentioned once
- Large gaps between related concepts
- Rapid topic shifts

## API Response Example

```json
{
  "complexityScore": 1.58,

  "graphMetrics": {
    "activatedNodeCount": 42,
    "uniqueEntityCount": 8,
    "totalMentionCount": 15,
    "entityRepetitionRatio": 1.875,
    "avgNodeDegree": 234.5,
    "relationshipsTraversed": 387,
    "avgExclusivityScore": 0.42,
    "firingRounds": 3
  },

  "activationMetrics": {
    "activationAtEncounter": 0.85,
    "activationAtEOS": 0.63,
    "activationAtEOP": 0.58,
    "activationAtEODoc": 0.52,
    "activationDecayRate": 0.388,
    "activationStability": 0.89
  },

  "distanceMetrics": {
    "avgTokenDistance": 12.3,
    "avgSentenceDistance": 1.4,
    "avgParagraphDistance": 0.2,
    "tokenCoherenceScore": 0.75
  },

  "structureMetrics": {
    "sentenceCount": 8,
    "paragraphCount": 3,
    "tokenCount": 142,
    "entitiesPerSentence": 1.875,
    "sentenceCoverageRatio": 0.875,
    "entityDensity": 0.106
  },

  "coherenceMetrics": {
    "rementionCount": 7,
    "avgRementionsPerEntity": 0.875,
    "entityPersistence": 5,
    "carriedForwardEntities": 4
  },

  "compositeScores": {
    "entityCohesionScore": 0.78,
    "semanticConnectivityScore": 0.65,
    "topicPersistenceScore": 0.82,
    "localCoherenceScore": 0.71,
    "globalCoherenceScore": 0.68
  }
}
```

## Technical Notes

### Data Already Available
All metrics can be computed from data already tracked during spreading activation:
- `tidalActivation` - All node activations
- `fired` / `burned` - Propagation tracking
- `currentAggregatedActivations` - Activation at each mention
- `activationsAtEncounter/EOS/EOP/EODoc` - Stage-based tracking
- `LinkedDocument` - Text structure (sentences, paragraphs, mentions)
- `deltaToken/Sentence/Paragraph` - Distance calculations

### No Additional Graph Queries Needed
Everything needed is already computed! Just need to:
1. Capture the intermediate values
2. Calculate statistics
3. Package into response

### Performance Impact
Minimal - just aggregating data already in memory. No extra graph traversals or spreading activation runs needed.

## Next Steps

1. Read this document thoroughly
2. Review existing code to understand data structures
3. Implement Phase 1 (Core Metrics)
4. Test with sample texts
5. Proceed to Phase 2 and 3 as needed

---

**Ready to implement?** Start with Phase 1 core metrics for maximum coherence measurement impact with minimal code changes.
