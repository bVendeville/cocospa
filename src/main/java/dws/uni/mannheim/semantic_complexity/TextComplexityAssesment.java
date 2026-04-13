package dws.uni.mannheim.semantic_complexity;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.neo4j.graphdb.Transaction;

import redis.clients.jedis.Jedis;
import dws.uni.mannheim.relatedness.EntityLinker;
import dws.uni.mannheim.semantic_complexity.spreading_activation.Mode;
import dws.uni.mannheim.semantic_complexity.spreading_activation.SAComplexityModes;

public class TextComplexityAssesment
{
    public static class ComplexityResult {
        public double complexityScore;
        public CoherenceMetrics coherenceMetrics;

        public ComplexityResult(double complexityScore, CoherenceMetrics coherenceMetrics) {
            this.complexityScore = complexityScore;
            this.coherenceMetrics = coherenceMetrics;
        }
    }

    public static ComplexityResult assess(Map<String, Mode> modesIndex, String text,
            boolean phiTo1, double linkerThreshold, String dbspotlightUrl)
    {
        
        try (Transaction tx = Application.db.beginTx())
        {

            KanopyDocument kdoc = EntityLinker.Link(text, dbspotlightUrl, linkerThreshold);

            SAComplexityModes samodes = new SAComplexityModes(Application.db);
            MentionExtractor extr = new MentionExtractor(Application.db,
                    Application.dbpedia, kdoc);
            //System.out.println("removing outliers");
            //extr.removeLikelyWrongLinks();
            //System.out.println("removed");

            FeaturedDocument fdoc = new FeaturedDocument(kdoc,
                    text, Application.nlpPipeline,
                    Application.db, Application.dbpedia, null, null, null);

            LinkedDocument ldoc = new LinkedDocument(fdoc,
                    text);

            Map<String, Double> activationsAtEncounter = new HashMap<>();
            Map<String, Double> activationAtEOS = new HashMap<>();
            Map<String, Double> activationAtEOP = new HashMap<>();
            Map<String, Double> activationAtEODoc = new HashMap<>();
            CoherenceMetrics metrics = null;
            try (Jedis jedis = Application.jedisPool.getResource())
            {
                metrics = samodes.computeComplexityWithSpreadingActivationOncePerEntity(
                        ldoc, modesIndex, phiTo1,
                        activationsAtEncounter, activationAtEOS,
                        activationAtEOP, activationAtEODoc, jedis);
            } catch (Exception ex)
            {
                ex.printStackTrace();
            }

            if (metrics == null) {
                metrics = new CoherenceMetrics();
            }

            for (Entry<String, Mode> me : modesIndex.entrySet())
            {
                System.out.println(String.valueOf(activationsAtEncounter.get(me.getKey())));
                System.out.println(String.valueOf(activationAtEOS.get(me.getKey())));
                System.out.println(String.valueOf(activationAtEOP.get(me.getKey())));
                System.out.println(String.valueOf(activationAtEODoc.get(me.getKey())));

                // Store activation values in metrics
                metrics.activationAtEncounter = activationsAtEncounter.get(me.getKey());
                metrics.activationAtEOS = activationAtEOS.get(me.getKey());
                metrics.activationAtEOP = activationAtEOP.get(me.getKey());
                metrics.activationAtEODoc = activationAtEODoc.get(me.getKey());

                // Now compute derived metrics that depend on activation values
                metrics.computeActivationDecay();
                metrics.computeActivationStability();
                // Recompute composite scores now that activation values are set
                metrics.computeCompositeScores();

                double simplicityValue = activationAtEOS.get(me.getKey());
                if (simplicityValue == 0.0 || Double.isNaN(simplicityValue))
                    return new ComplexityResult(-1, metrics);
                else
                {
                    return new ComplexityResult(1 / simplicityValue, metrics);
                }
            }
        }

        catch (Exception ex)
        {
            ex.printStackTrace();
        }

        return new ComplexityResult(-1, new CoherenceMetrics());
    }
}
