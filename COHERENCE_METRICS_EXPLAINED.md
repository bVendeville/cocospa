# Coherence Metrics - Detailed Explanation

This document explains exactly how each coherence metric is calculated, when it's computed, and what it represents.

---

## GRAPH CONNECTIVITY METRICS

### `activatedNodeCount` (integer)
- **What**: Total number of unique knowledge graph nodes that were activated during spreading activation
- **When**: Counted during the entire document processing
- **How**: Each time an entity mention triggers spreading activation, all reachable nodes in DBpedia are activated. This counts the unique nodes across all entity mentions.
- **Example**: If "Obama" activates nodes [President, USA, Hawaii] and "Trump" activates [President, USA, NYC], then activatedNodeCount = 4 (President, USA, Hawaii, NYC)

### `uniqueEntityCount` (integer)
- **What**: Number of distinct entities mentioned in the text (ignoring repetitions)
- **When**: Counted at document level
- **How**: Counts unique URIs from DBpedia Spotlight entity linking
- **Example**: "Obama was President. Obama served two terms" → uniqueEntityCount = 2 (Obama, President)

### `totalMentionCount` (integer)
- **What**: Total number of entity mentions including repetitions
- **When**: Counted at document level
- **How**: Every entity mention is counted, even if it's the same entity
- **Example**: "Obama was President. Obama served two terms" → totalMentionCount = 4 (Obama×2, President×1, terms×1)

### `entityRepetitionRatio` (double)
- **What**: How many times entities are mentioned on average
- **When**: Computed at document level after all mentions are counted
- **How**:
$$
\text{entityRepetitionRatio} = \frac{\text{totalMentionCount}}{\text{uniqueEntityCount}}
$$
- **Interpretation**:
  - 1.0 = each entity mentioned exactly once
  - 2.0 = each entity mentioned twice on average
  - Higher = more repetition (can indicate good coherence through topic continuity)

### `avgNodeDegree` (double)
- **What**: Average number of connections (edges) for activated nodes in the knowledge graph
- **When**: Computed during mention processing
- **How**: For each entity mention, get its node's degree (number of relationships) in DBpedia, then average across all mentions
- **Interpretation**: Higher values = entities are more connected in the knowledge graph (more general/important concepts)

---

## ACTIVATION DYNAMICS

These metrics measure semantic activation at **4 key reading points** for each entity mention, then **averaged across all mentions**.

### Reading Simulation Process
The system simulates a human reading the text linearly:
1. **At Encounter**: When you first see an entity in a sentence
2. **End of Sentence (EOS)**: After reading the complete sentence
3. **End of Paragraph (EOP)**: After reading the complete paragraph
4. **End of Document (EODoc)**: After reading the entire text

Between these points, activation **decays** based on:
- Token distance (words read)
- Sentence boundaries crossed
- Paragraph boundaries crossed

### `activationAtEncounter` (double, AVERAGE)
- **What**: Average activation level when re-encountering an entity
- **When**: Measured the moment an entity mention is encountered (for entities seen before)
- **How**:
  1. For each entity mention (after the first), check its current activation in memory
  2. This activation has decayed based on distance since last mention
  3. Average across all re-mentions
- **Interpretation**:
  - High = entities remain active in memory between mentions (good coherence)
  - Low = entities fade quickly (poor coherence)

### `activationAtEOS` (double, AVERAGE)
- **What**: Average activation level at the end of each sentence
- **When**: Measured after reading each complete sentence containing entities
- **How**:
  1. For each sentence with entity mentions, compute activation after decay to end of sentence
  2. Average across all such sentences
  3. **This is the value used for the complexity score** (complexity = 1/activationAtEOS)
- **Interpretation**: Higher = stronger semantic cohesion at sentence level

### `activationAtEOP` (double, AVERAGE)
- **What**: Average activation level at the end of each paragraph
- **When**: Measured after reading each complete paragraph containing entities
- **How**: Similar to EOS, but measured at paragraph boundaries
- **Interpretation**: Indicates how well concepts persist across paragraphs

### `activationAtEODoc` (double, AVERAGE)
- **What**: Average activation level at the very end of the document
- **When**: Measured after reading the entire text
- **How**: Final activation values for all entity mentions after full decay
- **Interpretation**:
  - Often very small due to decay over long distances
  - Indicates which concepts remain "active in memory" after reading

### `activationDecayRate` (double, 0-1)
- **What**: Percentage of activation lost from encounter to document end
- **When**: Computed at document level
- **How**:
$$
\text{activationDecayRate} = \frac{\text{activationAtEncounter} - \text{activationAtEODoc}}{\text{activationAtEncounter}}
$$
- **Interpretation**:
  - 0.0 = no decay (perfect retention)
  - 1.0 = complete decay (nothing remains in memory)
  - Typical: 0.8-1.0 for long documents

### `activationStability` (double, 0-1)
- **What**: How consistent activation levels are across the 4 reading stages
- **When**: Computed at document level
- **How**:
  1. Calculate mean: $\mu = \frac{1}{4}(\text{activationAtEncounter} + \text{EOS} + \text{EOP} + \text{EODoc})$
  2. Calculate standard deviation: $\sigma = \sqrt{\frac{1}{4}\sum_{i=1}^{4}(x_i - \mu)^2}$
  3. Coefficient of variation:
$$
\text{activationStability} = \max\left(0, 1 - \frac{\sigma}{\mu}\right)
$$
- **Interpretation**:
  - 1.0 = perfectly stable (same activation throughout)
  - 0.0 = highly variable (activation spikes and drops)

---

## ACTIVATION STATISTICS

These describe the **distribution** of activation values **at End-of-Sentence** across all entity mentions.

### `activationStdDev` (double)
- **What**: Standard deviation of EOS activation values
- **When**: Computed from the list of all EOS activation values (one per entity mention)
- **How**: Standard statistical standard deviation across the list
- **Interpretation**: Higher = more variation in how entities are activated

### `activationMin` (double)
- **What**: Minimum EOS activation value (excluding zeros)
- **When**: Computed from EOS activations
- **How**: `min(all non-zero EOS activation values)`

### `activationMax` (double)
- **What**: Maximum EOS activation value
- **When**: Computed from EOS activations
- **How**: `max(all EOS activation values)`

### `activationMedian` (double)
- **What**: Median EOS activation value
- **When**: Computed from EOS activations
- **How**: Sort all EOS values and take the middle one
- **Interpretation**: Less sensitive to outliers than mean

---

## DISTANCE METRICS

These measure **proximity between consecutive entity mentions**.

### `avgTokenDistance` (double)
- **What**: Average number of tokens (words) between consecutive entity mentions
- **When**: Computed during reading simulation
- **How**:
$$
\text{avgTokenDistance} = \frac{1}{n-1}\sum_{i=2}^{n} (\text{mention}_i.\text{tokenEnd} - \text{mention}_{i-1}.\text{tokenEnd})
$$
  where $n$ is the total number of mentions
- **Example**: "Obama [5 words] President [3 words] USA" → avgTokenDistance = 4.0
- **Interpretation**: Lower = entities are closer together (better coherence)

### `stdTokenDistance` (double)
- **What**: Standard deviation of token distances between consecutive mentions
- **When**: Computed during reading simulation
- **How**:
$$
\text{stdTokenDistance} = \sqrt{\frac{1}{n-1}\sum_{i=1}^{n-1} (d_i - \text{avgTokenDistance})^2}
$$
  where $d_i$ is the token distance for pair $i$
- **Interpretation**: Higher = more variability in spacing between entity mentions

### `maxTokenDistance` (double)
- **What**: Largest gap in tokens between any two consecutive mentions
- **Interpretation**: Indicates the longest "entity-free" stretch

### `avgSentenceDistance` (double)
- **What**: Average number of sentences between consecutive entity mentions
- **How**: Similar to token distance, but counts sentence boundaries crossed
- **Example**: Mention in S1, next mention in S3 → distance = 2
- **Interpretation**: Lower = better local coherence

### `stdSentenceDistance` (double)
- **What**: Standard deviation of sentence distances between consecutive mentions
- **How**:
$$
\text{stdSentenceDistance} = \sqrt{\frac{1}{n-1}\sum_{i=1}^{n-1} (d_i - \text{avgSentenceDistance})^2}
$$
- **Interpretation**: Higher = more variability in sentence-level spacing

### `maxSentenceDistance` (double)
- **What**: Maximum sentence gap between consecutive mentions

### `avgParagraphDistance` (double)
- **What**: Average number of paragraphs between consecutive entity mentions
- **Interpretation**: Lower = entities revisited more frequently across paragraphs

### `stdParagraphDistance` (double)
- **What**: Standard deviation of paragraph distances between consecutive mentions
- **How**:
$$
\text{stdParagraphDistance} = \sqrt{\frac{1}{n-1}\sum_{i=1}^{n-1} (d_i - \text{avgParagraphDistance})^2}
$$
- **Interpretation**: Higher = more variability in paragraph-level spacing

### `maxParagraphDistance` (double)
- **What**: Maximum paragraph gap

### `tokenCoherenceScore` (double, 0-1)
- **What**: Normalized proximity score based on token distance
- **When**: Computed at document level
- **How**:
$$
\text{tokenCoherenceScore} = \frac{1}{1 + \text{avgTokenDistance}}
$$
- **Interpretation**:
  - 1.0 = mentions are adjacent (distance=0)
  - 0.5 = 1 token apart on average
  - 0.0 = mentions very far apart

---

## TEXT STRUCTURE METRICS

Simple counts computed at document level.

### `sentenceCount`, `paragraphCount`, `tokenCount` (integers)
- **What**: Total counts of structural units
- **How**:
  - Sentences: from Stanford CoreNLP sentence splitting
  - Paragraphs: from text structure (double newlines)
  - Tokens: approximated by splitting on non-word characters

### `avgSentenceLength` (double)
- **How**:
$$
\text{avgSentenceLength} = \frac{\text{tokenCount}}{\text{sentenceCount}}
$$

### `avgParagraphLength` (double)
- **How**:
$$
\text{avgParagraphLength} = \frac{\text{sentenceCount}}{\text{paragraphCount}}
$$

### `entityDensity` (double, 0-1)
- **What**: What fraction of the text consists of entity mentions
- **How**:
$$
\text{entityDensity} = \frac{\text{totalMentionCount}}{\text{tokenCount}}
$$
- **Interpretation**:
  - 0.1 = 10% of tokens are entity mentions
  - Higher = more entity-rich text

---

## ENTITY DISTRIBUTION METRICS

### `entitiesPerSentence` (double)
- **What**: Average number of entity mentions per sentence
- **How**:
$$
\text{entitiesPerSentence} = \frac{\text{totalMentionCount}}{\text{sentenceCount}}
$$

### `entitiesPerParagraph` (double)
- **What**: Average number of entity mentions per paragraph
- **How**:
$$
\text{entitiesPerParagraph} = \frac{\text{totalMentionCount}}{\text{paragraphCount}}
$$

### `sentencesWithEntities` (integer)
- **What**: Number of sentences containing at least one entity mention
- **How**: Count sentences that have ≥1 entity

### `paragraphsWithEntities` (integer)
- **What**: Number of paragraphs containing at least one entity mention

### `sentenceCoverageRatio` (double, 0-1)
- **What**: Percentage of sentences that contain entities
- **How**:
$$
\text{sentenceCoverageRatio} = \frac{\text{sentencesWithEntities}}{\text{sentenceCount}}
$$
- **Interpretation**: Higher = entities more evenly distributed

### `paragraphCoverageRatio` (double, 0-1)
- **What**: Percentage of paragraphs that contain entities
- **How**:
$$
\text{paragraphCoverageRatio} = \frac{\text{paragraphsWithEntities}}{\text{paragraphCount}}
$$

---

## CONCEPT REACTIVATION

### `rementionCount` (integer)
- **What**: Total number of times any entity is mentioned AGAIN (after first mention)
- **When**: Computed at document level
- **How**: For each entity, count mentions beyond the first, then sum
- **Example**:
  - "Obama" mentioned 3 times → contributes 2 to rementionCount
  - "Trump" mentioned 2 times → contributes 1 to rementionCount
  - Total rementionCount = 3
- **Interpretation**: Higher = more entity repetition (can indicate coherence through topic continuity)

### `avgRementionsPerEntity` (double)
- **What**: Average number of times each entity is re-mentioned
- **How**:
$$
\text{avgRementionsPerEntity} = \frac{\text{rementionCount}}{\text{uniqueEntityCount}}
$$
- **Example**: 5 entities, 10 total rementions → 2.0 average

### `entityPersistence` (integer)
- **What**: Number of entities that appear in **multiple paragraphs**
- **When**: Computed at document level
- **How**:
  1. For each entity, track which paragraphs it appears in
  2. Count entities appearing in ≥2 paragraphs
- **Interpretation**: Higher = concepts maintained across document structure

### `carriedForwardEntities` (integer)
- **What**: Number of times an entity appears in **consecutive paragraphs**
- **When**: Computed at document level
- **How**:
  1. For each paragraph pair (P1→P2, P2→P3, etc.)
  2. Count entities appearing in both paragraphs
  3. Sum across all paragraph transitions
- **Example**:
  - P1 has [Obama, USA], P2 has [Obama, Trump], P3 has [Trump]
  - P1→P2: Obama appears in both (+1)
  - P2→P3: Trump appears in both (+1)
  - carriedForwardEntities = 2
- **Interpretation**: Measures local coherence at paragraph boundaries

---

## COMPOSITE COHERENCE SCORES

These combine multiple metrics into holistic coherence measures.

### `entityCohesionScore` (double)
- **What**: How well entities are re-used with proximity and maintained activation
- **When**: Computed at document level after all other metrics
- **How**:
$$
\text{entityCohesionScore} = \frac{\text{rementionCount}}{\text{uniqueEntityCount}} \times \frac{1}{1 + \text{avgSentenceDistance}} \times \max(\text{activationAtEOS}, \text{activationAtEODoc})
$$
- **Components**:
  - **Remention rate**: More re-use = higher score
  - **Proximity**: Closer mentions = higher score
  - **Activation**: Higher maintained activation = higher score
- **Interpretation**: Higher = strong entity-based coherence

### `semanticConnectivityScore` (double)
- **What**: How well the text activates connected concepts in the knowledge graph
- **When**: Computed at document level
- **How**:
  - **If graph metrics available**:
$$
\text{semanticConnectivityScore} = \frac{\text{activatedNodeCount}}{\text{uniqueEntityCount}} \times \text{avgExclusivityScore} \times \frac{\text{relationshipsTraversed}}{\text{activatedNodeCount}}
$$
  - **Fallback** (current implementation):
$$
\text{semanticConnectivityScore} = \frac{\text{activatedNodeCount}}{\text{uniqueEntityCount}}
$$
- **Interpretation**:
  - 1.0 = each entity activated exactly one node (no spreading)
  - >1.0 = entities activated additional related concepts (good semantic connectivity)

### `topicPersistenceScore` (double)
- **What**: How well topics are maintained across the document
- **When**: Computed at document level
- **How**:
$$
\text{topicPersistenceScore} = \frac{\text{entityPersistence}}{\text{uniqueEntityCount}} \times \text{topicStrength} \times \text{sentenceCoverageRatio}
$$
 - where:
   - $
\text{topicStrength} = \begin{cases}
\max(\text{activationAtEOS}, \text{activationAtEOP}) & \text{if } > 10^{-10} \\
1 - \text{activationDecayRate} & \text{otherwise (fallback)}
\end{cases}
$
- **Components**:
  - **Entity persistence**: Entities in multiple paragraphs
  - **Topic strength**: Using activation levels (more robust than decay rate)
  - **Coverage**: Entities well-distributed
- **Interpretation**: Higher = strong topic continuity

### `localCoherenceScore` (double)
- **What**: Sentence-to-sentence connectivity
- **When**: Computed at document level
- **How**:
$$
\text{localCoherenceScore} = \frac{1}{\text{avgSentenceDistance}} \times \text{entitiesPerSentence} \times \frac{\text{carriedForwardEntities}}{\text{sentenceCount}}
$$
- **Components**:
  - **Proximity**: Close mentions
  - **Density**: More entities per sentence
  - **Carry-forward**: Entities cross paragraph boundaries
- **Interpretation**: Higher = strong local coherence

### `globalCoherenceScore` (double)
- **What**: Document-wide semantic unity
- **When**: Computed at document level
- **How**:
$$
\text{globalCoherenceScore} = \text{activation} \times \text{paragraphCoverageRatio} \times \frac{\text{entityPersistence}}{\text{paragraphCount}}
$$
 - where:
   
   - $
\text{activation} = \begin{cases}
\max(\text{activationAtEOP}, \text{activationAtEODoc}) & \text{if } > 10^{-10} \\
\text{activationAtEOS} & \text{otherwise (fallback)}
\end{cases}
$
- **Components**:
  - **Activation**: Strong maintained activation
  - **Coverage**: Entities across paragraphs
  - **Persistence**: Entities span multiple paragraphs
- **Interpretation**: Higher = strong overall document coherence

---

## PAIRWISE ENTITY RELATEDNESS

These metrics measure how strongly entities are connected through the knowledge graph by tracking cross-activation patterns during spreading activation.

### Document-Level Pairwise Relatedness

**`avgPairwiseRelatedness` (double)**
- **What**: Average cross-activation strength between all entity pairs in the document
- **When**: Computed during spreading activation (no extra cost)
- **How**:
  - When entity A is activated, spreading activation naturally reaches other entities
  - If entity B is also in the text, we record the activation A→B
  - Average across all entity pairs with non-zero cross-activation
$$
\text{avgPairwiseRelatedness} = \frac{1}{|\text{pairs}|} \sum_{(i,j) \in \text{pairs}} \text{crossActivation}_{i \to j}
$$
- **Interpretation**:
  - Higher values = entities are semantically related in the knowledge graph
  - Low values (<0.01) = distant/unrelated entities
  - High values (>0.1) = tightly clustered, related concepts

**`avgPairwiseRelatednessWeightedByMaxActivation` (double)**
- **What**: Pairwise relatedness weighted by entity prominence
- **How**: Same as above, but weighted by max(activation_A, activation_B) for each pair
$$
\text{weighted} = \frac{1}{|\text{pairs}|} \sum_{(i,j)} \text{crossActivation}_{i \to j} \times \max(\text{maxActivation}_i, \text{maxActivation}_j)
$$
- **Interpretation**: Emphasizes relationships between highly activated (prominent) entities

**`maxPairwiseRelatedness` (double)**
- **What**: Strongest entity-entity connection found
- **Interpretation**: Identifies the two most closely related entities in the text

**`entityPairCount` (integer)**
- **What**: Number of entity pairs with non-zero cross-activation
- **Interpretation**: Context for understanding the averages above

### Paragraph-Level Pairwise Relatedness

**`avgPairwiseRelatednessPerParagraph` (double)**
- **What**: Average local coherence through entity relatedness within paragraphs
- **How**:
  1. For each paragraph, filter entity pairs to only those where both entities appear in that paragraph
  2. Compute average pairwise relatedness for the paragraph
  3. Average across all paragraphs
$$
\text{perParagraph} = \frac{1}{P} \sum_{p=1}^{P} \left( \frac{1}{|\text{pairs}_p|} \sum_{(i,j) \in \text{pairs}_p} \text{crossActivation}_{i \to j} \right)
$$
  where $P$ = number of paragraphs with ≥2 entities
- **Interpretation**: Measures local semantic coherence at paragraph level

**`avgPairwiseRelatednessPerParagraphWeightedByActivation` (double)**
- **What**: Paragraph-level weighted relatedness, then averaged across paragraphs
- **How**: For each paragraph, compute the weighted pairwise relatedness (same formula as document-level), then average across paragraphs
$$
\text{weightedPerParagraph} = \frac{1}{P} \sum_{p=1}^{P} \left( \frac{1}{|\text{pairs}_p|} \sum_{(i,j) \in \text{pairs}_p} \text{crossActivation}_{i \to j} \times \max(\text{activation}_i, \text{activation}_j) \right)
$$
  where $P$ = number of paragraphs with ≥2 entities
- **Interpretation**: Measures how strongly related prominent entities are within each paragraph, averaged across all paragraphs

### Why These Metrics Matter

- **Computational Efficiency**: Leverages existing spreading activation data (no extra graph queries)
- **Semantic Coherence**: Directly measures if text entities are conceptually related
- **Granularity**: Both document-level (global) and paragraph-level (local) perspectives
- **Prominence Weighting**: Optional weighting ensures prominent entities influence the score more

---

## PER-ENTITY COHERENCE SCORES

These metrics provide an alternative calculation approach that addresses the mathematical non-equivalence between "product of averages" and "average of products".

### Mathematical Difference: Aggregate vs Per-Entity

**Aggregate Approach** (Product of Averages):
1. Calculate average rementions across all entities
2. Calculate average sentence distance across all mention pairs
3. Calculate average activation across all mentions
4. Multiply these averages together

$$
\text{Score}_{\text{aggregate}} = \bar{r} \times \frac{1}{1 + \bar{d}} \times \bar{a}
$$

where $\bar{r}$ = average rementions, $\bar{d}$ = average distance, $\bar{a}$ = average activation

Example with 2 entities:
- Entity A: 5 rementions, avg distance = 2 sentences, activation = 1.0
- Entity B: 1 remention, avg distance = 10 sentences, activation = 0.5

Aggregate: $3 \times \frac{1}{1+6} \times 0.75 = 0.32$

**Per-Entity Approach** (Average of Products):
1. For each entity separately, calculate its coherence score
2. Average (or take median of) these per-entity scores

$$
\text{Score}_{\text{per-entity-mean}} = \frac{1}{N}\sum_{i=1}^{N} \left( r_i \times \frac{1}{1 + d_i} \times a_i \right)
$$

$$
\text{Score}_{\text{per-entity-median}} = \text{median}\left\{ r_i \times \frac{1}{1 + d_i} \times a_i \right\}_{i=1}^{N}
$$

where $N$ = number of entities, $r_i$ = rementions for entity $i$, $d_i$ = avg distance for entity $i$, $a_i$ = activation for entity $i$

Same example:
- Entity A score: $5 \times \frac{1}{1+2} \times 1.0 = 1.67$
- Entity B score: $1 \times \frac{1}{1+10} \times 0.5 = 0.045$
- Per-Entity Mean: $\frac{1.67 + 0.045}{2} = 0.86$
- Per-Entity Median: $\text{median}(1.67, 0.045) = 0.86$

**Why This Matters:**
- Aggregate scores can mask individual entity patterns (Entity A's high coherence is diluted by Entity B)
- Per-entity scores better capture the distribution of coherence across entities
- Median is less sensitive to outliers than mean
- Both approaches provide valuable but different insights

### When to Use Which

- **Aggregate scores**: Good for overall document-level coherence assessment
- **Per-entity mean**: Captures average coherence behavior across all entities
- **Per-entity median**: Robust to outliers, represents "typical" entity coherence
- **Use both**: For comprehensive analysis (PCA can determine which matters most)

### `entityCohesionScore_PerEntity` (Mean & Median)
- **What**: Per-entity cohesion scores aggregated across all entities
- **When**: Computed after all mentions are processed
- **How**: For each entity $i$, compute:
$$
\text{Score}_i = \frac{r_i \times \max(a_{i,\text{EOS}}, a_{i,\text{EODoc}})}{1 + d_i}
$$
  where $r_i$ = rementions, $d_i$ = avgSentenceDistance, $a_i$ = activation

  Then aggregate:
  - **Mean**: $\frac{1}{N}\sum_{i=1}^{N} \text{Score}_i$ — Average coherence across entities
  - **Median**: $\text{median}\{\text{Score}_i\}$ — Typical entity, robust to outliers

- **Interpretation**: Higher = entities consistently re-used with proximity and maintained activation

### `topicPersistenceScore_PerEntity` (Mean & Median)
- **What**: Per-entity topic persistence aggregated across all entities
- **When**: Computed after all mentions are processed
- **How**: For each entity $i$, compute:
$$
\text{Score}_i = p_i \times (1 - \text{decay}_i) \times c_i
$$
  where:
  - $p_i = 1$ if entity $i$ appears in multiple paragraphs, else $0$
  - $\text{decay}_i = \frac{a_{i,\text{EOS}} - a_{i,\text{EODoc}}}{a_{i,\text{EOS}}}$ (entity-specific decay)
  - $c_i = \frac{\text{mentions of entity } i}{\text{total sentences}}$ (coverage)

  Then aggregate:
  - **Mean**: $\frac{1}{N}\sum_{i=1}^{N} \text{Score}_i$ — Average persistence
  - **Median**: $\text{median}\{\text{Score}_i\}$ — Typical persistence, robust to outliers

- **Interpretation**: Higher = entities maintain consistent presence and activation throughout document

### `localCoherenceScore_PerEntity` (Mean & Median)
- **What**: Per-entity local coherence aggregated across all entities
- **When**: Computed after all mentions are processed
- **How**: For each entity $i$, compute:
$$
\text{Score}_i = \frac{\text{density}_i \times \text{consecutive}_i}{d_i}
$$
  where:
  - $d_i$ = avgSentenceDistance for entity $i$
  - $\text{density}_i = \frac{\text{mentions of entity } i}{\text{totalSentences}}$
  - $\text{consecutive}_i = \frac{\text{mentions in consecutive sentences}}{\text{totalSentences}}$

  Then aggregate:
  - **Mean**: $\frac{1}{N}\sum_{i=1}^{N} \text{Score}_i$ — Average local coherence
  - **Median**: $\text{median}\{\text{Score}_i\}$ — Typical pattern, robust to outliers

- **Interpretation**: Higher = entities appear in close proximity with frequent consecutive mentions

### `globalCoherenceScore_PerEntity` (Mean & Median)
- **What**: Per-entity global coherence aggregated across all entities
- **When**: Computed after all mentions are processed
- **How**: For each entity $i$, compute:
$$
\text{Score}_i = a_i \times \text{paragraphCoverage}_i \times p_i
$$
  where:
  - $a_i = \max(a_{i,\text{EOP}}, a_{i,\text{EODoc}})$ with fallback to $a_{i,\text{EOS}}$
  - $\text{paragraphCoverage}_i = \frac{\text{unique paragraphs with entity } i}{\text{totalParagraphs}}$
  - $p_i = 1$ if entity $i$ appears in 2+ paragraphs, else $0$

  Then aggregate:
  - **Mean**: $\frac{1}{N}\sum_{i=1}^{N} \text{Score}_i$ — Average global coherence
  - **Median**: $\text{median}\{\text{Score}_i\}$ — Typical pattern, robust to outliers

- **Interpretation**: Higher = entities maintain strong activation and presence across document structure

---

## PROCESSING TIMELINE

**Stage 1: Entity Linking**
- DBpedia Spotlight identifies entities in text
- Each mention gets a URI (e.g., `http://dbpedia.org/resource/Barack_Obama`)

**Stage 2: Spreading Activation (per entity, cached in Redis)**
- For each unique entity, run spreading activation on DBpedia graph
- Store activation values for all reachable nodes
- This happens ONCE per entity (cached for reuse)

**Stage 3: Reading Simulation (sequential)**
- Process mentions in reading order (left to right, sentence by sentence)
- For each mention:
  - Apply decay to current memory (based on distance since last mention)
  - Record activation at encounter (if re-mention)
  - Add new spreading activation from current entity
  - Track distances (token, sentence, paragraph)
- At end of sentence: record EOS activations
- At end of paragraph: record EOP activations
- At end of document: record EODoc activations

**Stage 4: Aggregation & Computation**
- Count structural metrics (sentences, paragraphs, tokens)
- Compute distance averages
- Compute entity distribution metrics
- Compute activation statistics (from EOS values)
- Compute persistence and carry-forward metrics
- Compute composite scores

---

## KEY INSIGHTS

1. **Activation metrics are AVERAGES** across all entity mentions at specific reading points
2. **Distance metrics are AVERAGES** across consecutive mention pairs
3. **Everything is processed ONCE** in linear reading order, then aggregated
4. **Composite scores combine multiple dimensions** of coherence
5. **Most metrics are 0 for single-paragraph texts** (no paragraph-level coherence to measure)
6. **Two calculation approaches available**:
   - **Aggregate scores**: Product of averages (document-level overview)
   - **Per-entity scores**: Average/median of per-entity products (captures distribution)
7. **Mean vs Median**:
   - Mean is sensitive to outliers (one highly coherent entity can skew results)
   - Median represents typical entity behavior
   - Both are provided for per-entity scores

---

## LIMITATIONS

- `relationshipsTraversed`, `avgExclusivityScore`, `firingRoundsCount` are currently not tracked (would require modifying the Redis caching layer)
- Very long texts may have `activationAtEODoc ≈ 0` due to natural decay
- Single-paragraph texts have limited coherence metrics (no cross-paragraph measures)
