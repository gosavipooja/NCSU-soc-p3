package referral_helper;

public class Utils {
	public static void updateExpertise(
			final double[] query,
			final double[] answer, 
			double[] expertise) 
	{
		assert answer.length == query.length;
		assert answer.length == expertise.length;
		for (int i=0; i < answer.length; i++)
		{
			expertise[i] = answer[i];
		}
	}
	
	public static void updateSociability(
			final double[] query,
			final double[] answer,
			int distance2tail,
			double[] sociability) 
	{
		assert query.length == answer.length;
		assert query.length == sociability.length;
		assert distance2tail > 0;
		
		for (int i=0; i < answer.length; i++)
		{
			double rate = 1.0 / (1 + distance2tail);
			sociability[i] = (1-rate) * sociability[i] + rate * answer[i];
		}
	}
	
	public static boolean isExpertiseMatch(
			final double[] expertise, 
			final double[] query) 
	{
		assert expertise.length == query.length;
		for (int i=0; i < expertise.length; i++)
		{
			if (expertise[i] / query[i] < 0.5) return false;
		}
		return true;
	}
	
	public static double[] genAnswer(
			final double[] expertise, 
			final double[] query) 
	{
		return expertise.clone();
	}
	
	public static int getMaxNumOfNeighbors() {
		return 2;
	}
	
	public static double getWeightOfSociability(){
		return 0.5;
	}
}
