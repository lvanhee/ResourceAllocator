package solver;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import input.ProblemInstance;
import model.UserResourceTypeAllocation;

public class SatisfactionMeasure {
	
	private final Map<Integer, Integer> nbPerSatisfaction;
	
	private SatisfactionMeasure(Map<Integer, Integer> nbPerSatisfaction) {
		this.nbPerSatisfaction = nbPerSatisfaction;
	}

	public static SatisfactionMeasure newInstance(Set<UserResourceInstanceAllocation> optimal,
			ProblemInstance input) {
		Map<Integer, Integer> nbPerSatisfaction = new HashMap<>();
		for(UserResourceInstanceAllocation ua : optimal)
		{
			int satisfactionOfAlloc = input.getAllocationsPerResourceType()
					.get(UserResourceTypeAllocation.newInstance(ua));
			if(!nbPerSatisfaction.containsKey(satisfactionOfAlloc))
				nbPerSatisfaction.put(satisfactionOfAlloc, 0);
			nbPerSatisfaction.put(satisfactionOfAlloc, nbPerSatisfaction.get(satisfactionOfAlloc)+1);			
		}
		
		return new SatisfactionMeasure(nbPerSatisfaction);
	}

	public int getWorseAllocationValue() {
		return
				nbPerSatisfaction.keySet().stream()
				.reduce(0, (x,y)->Integer.max(x, y));
	}
	
	public String toString()
	{
		return nbPerSatisfaction.toString();
	}
	
	public boolean equals(Object o)
	{
		return ((SatisfactionMeasure)o).nbPerSatisfaction.equals(nbPerSatisfaction);
	}

}
