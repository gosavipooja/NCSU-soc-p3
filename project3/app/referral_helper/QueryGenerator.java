package referral_helper;

import java.util.Random;
import java.lang.Math;

public class QueryGenerator {
	public static final long defaultSeed = 0;
	public static QueryGenerator getInstance() {
		return new QueryGenerator();
	}
	
	public void setSeed(long seed) {
		random.setSeed(seed);
	}
	
	private static String lastAgentName = "";
	public double[] genQuery(String actorName, double[] needsVec) {
		assert actorName.compareTo(lastAgentName) >= 0  : "Queries must be generated in agents' alphabetic order";
		lastAgentName = actorName;
		
		double[] newVec = needsVec.clone();
		
		for (int i=0; i<needsVec.length; i++)
		{
			assert needsVec[i] >= 0 && needsVec[i] <= 1 : "Value of needs must be between 0 and 1";
			double jitter = random.nextGaussian();
			if (jitter > 0)
			{
				newVec[i] += (1-newVec[i]) * Math.tanh(jitter);
			}
			else
			{
				newVec[i] += newVec[i] * Math.tanh(jitter);
			}
		}
		
		return newVec;
	}
	
	private QueryGenerator() {
		random = new Random();
		random.setSeed(defaultSeed);
	}
	private Random random;
}
