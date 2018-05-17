import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;

public class Main {
	
	private static final class Allocation
	{
		private final String receiver;
		private final String resource;
		public Allocation(String pl, String role) {
			this.receiver = pl;
			this.resource = role;
		}
		
		public int hashCode()
		{
			int res = receiver.hashCode()+resource.hashCode();
			return res;
		}
		
		public boolean equals(Object o)
		{
			Allocation a = (Allocation)o;
			return a.receiver.equals(receiver) && a.resource.equals(resource);
		}
		
		public String toString()
		{
			return receiver+":"+resource;
		}
		
	}

	/**
	 * -ea -Djava.library.path=/export/home/vanhee/Documents/software/cplex/cplex_studio/cplex/bin/x86-64_linux
	 * @param args
	 */
	public static void main(String[] args)
	{
		
		if(args.length < 1)
		{
			System.out.println("Please provide the interest input file");
			throw new Error();
		}
		
		Map<Allocation, Integer> rawPrefsPerAllocation = fromPosNegToPreferences(initPrefs(args[0]));
		
		Set<String> requesters = 
				rawPrefsPerAllocation
				.keySet()
				.stream()
				.map(x->x.receiver)
				.collect(Collectors.toSet());
		
		Set<String> resources = 
				rawPrefsPerAllocation.keySet()
				.stream()
				.map(x->x.resource)
				.collect(Collectors.toSet());
		if(requesters.size() > resources.size()) 
			throw new Error("Some requesters cannot be satisfied, not enough resources");


		//sometimes hard constraints are useful
		Set<Allocation> hardConstraints = new HashSet<>();

		Optional<Set<Allocation>> res = Optional.empty();
		int worseCase = 0;
		while(!res.isPresent())
		{
			worseCase ++;
			System.out.println("Trying to find an allocation with least satisfaction rank of:"+worseCase);
			res= optimizeAccordingToMaxInsatisfaction(rawPrefsPerAllocation, hardConstraints, worseCase);
		}

		List<Allocation> sortedList = new LinkedList<>();
		sortedList.addAll(res.get());
		sortedList.sort((x,y)-> x.receiver.compareTo(y.receiver));
		for(Allocation a:sortedList)
			System.out.println(a+" rank:"+rawPrefsPerAllocation.get(a));

			
			
	}

	private static Optional<Set<Allocation>> optimizeAccordingToMaxInsatisfaction(
			Map<Allocation, Integer> prefsPerAllocation,
			Set<Allocation> hardConstraints, int maxInsatisfaction)
	{

		try {
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);
			
			Set<Allocation> allowedAllocations = 
					prefsPerAllocation.keySet().stream().filter(x->prefsPerAllocation.get(x)<=maxInsatisfaction).collect(Collectors.toSet());

			SortedSet<String> objectsToBeAllocatedSomething = new TreeSet<>();
			objectsToBeAllocatedSomething.addAll(
					allowedAllocations.stream().map(x->x.receiver).collect(Collectors.toSet()));

			SortedSet<String> objectsToBeAllocated = new TreeSet<>();
			objectsToBeAllocated.addAll(
					allowedAllocations.stream().map(x->x.resource).collect(Collectors.toSet()));
			
			Map<Allocation, IloNumVar> varPerAlloc = generateVariablePerAllocation(cplex,
					allowedAllocations
					);

			for(String pl:objectsToBeAllocatedSomething)
			{
				int worseAllocValue = 1;
				if(allowedAllocations.stream()
						.anyMatch(x-> x.receiver.equals(pl)))
				{
					Allocation worseAlloc = allowedAllocations.stream()
							.filter(x-> x.receiver.equals(pl))
							.max((x,y)->prefsPerAllocation.get(x)-prefsPerAllocation.get(y)).get();
					worseAllocValue = prefsPerAllocation.get(worseAlloc);
				}

				for(Allocation a: allowedAllocations)
					if(!allowedAllocations.contains(a) && a.receiver.equals(pl))
						prefsPerAllocation.put(a, worseAllocValue+1);
			}


			IloNumExpr valToOptimize =  cplex.constant(0);
			for(Allocation a:allowedAllocations)
			{
				valToOptimize = 
						cplex.sum(
								valToOptimize,
								cplex.prod(
										Math.pow(objectsToBeAllocatedSomething.size(), prefsPerAllocation.get(a))+1,
										varPerAlloc.get(a)));
			}
			IloNumExpr[] weightedAllocations = new IloIntExpr[prefsPerAllocation.size()];

			int i = 0;
			for(Allocation a: varPerAlloc.keySet())
			{
				weightedAllocations[i] = cplex.prod(
						varPerAlloc.get(a),
						prefsPerAllocation.get(a));
				i++;
			}

			cplex.addMinimize(valToOptimize);

			generateAllocationConstraints(cplex,
					allowedAllocations,
					varPerAlloc
					);

			generateHardAllocationsConstraints(cplex, varPerAlloc, hardConstraints);



			cplex.exportModel("mipex1.lp");

			if (! cplex.solve() )
				return Optional.empty();
			System.out.println("Solution status = " + cplex.getStatus());
			Set<Allocation>s=
					processCplexResults(cplex, varPerAlloc);
			
			

			//s.sort((x,y)->roles.indexOf(x.role) - roles.indexOf(y.role));
			System.out.println("Minimizing the number of least happy people:");
			printOutput(s,prefsPerAllocation);
			cplex.end();
			
			return Optional.of(s);

		}
		catch (IloException e) {
			System.err.println("Concert exception caught '" + e + "' caught");
			throw new Error();
		}
	}

	private static void generateHardAllocationsConstraints(IloCplex cplex, Map<Allocation, IloNumVar> varPerAlloc,
			Set<Allocation> hardConstraints) throws IloException {
		for(Allocation hc: hardConstraints)
			cplex.addEq(1,
					varPerAlloc.get(hc), "HardConstraint("+hc+")");
	}

/*	private static int getMaxInsatisfaction(Map<Allocation, Integer> prefsPerAllocation) {
		{
			try {
				IloCplex cplex = new IloCplex();

				SortedSet<String> objectsToBeAllocatedSomething = new TreeSet<>();
				objectsToBeAllocatedSomething.addAll(
						prefsPerAllocation.keySet().stream().map(x->x.receiver).collect(Collectors.toSet()));

				SortedSet<String> objectsToBeAllocated = new TreeSet<>();
				objectsToBeAllocated.addAll(
						prefsPerAllocation.keySet().stream().map(x->x.resource).collect(Collectors.toSet()));
				
				Map<Allocation, IloNumVar> varPerAlloc = generateVariablePerAllocation(cplex,
						prefsPerAllocation.keySet()
						);

				for(String pl:objectsToBeAllocatedSomething)
				{
					int worseAllocValue = 1;
					if(prefsPerAllocation.keySet().stream()
							.anyMatch(x-> x.receiver.equals(pl)))
					{
						Allocation worseAlloc = prefsPerAllocation.keySet().stream()
								.filter(x-> x.receiver.equals(pl))
								.max((x,y)->prefsPerAllocation.get(x)-prefsPerAllocation.get(y)).get();
						worseAllocValue = prefsPerAllocation.get(worseAlloc);
					}

					for(Allocation a: varPerAlloc.keySet())
						if(!prefsPerAllocation.containsKey(a) && a.receiver.equals(pl))
							prefsPerAllocation.put(a, worseAllocValue+1);
				}


				IloNumExpr valToOptimize =  cplex.constant(0);
				IloNumExpr[] weightedAllocations = new IloIntExpr[prefsPerAllocation.size()];

				int i = 0;
				for(Allocation a: varPerAlloc.keySet())
				{
					weightedAllocations[i] = cplex.prod(
							varPerAlloc.get(a),
							prefsPerAllocation.get(a));
					i++;
				}
				valToOptimize = cplex.max(weightedAllocations);
				valToOptimize = cplex.prod(valToOptimize, 100000);
				valToOptimize = cplex.sum(valToOptimize,cplex.sum(weightedAllocations));

				cplex.addMinimize(valToOptimize);

				generateAllocationConstraints(cplex,
						prefsPerAllocation.keySet(),
						varPerAlloc
						);




				cplex.exportModel("mipex1.lp");

				if (! cplex.solve() ) throw new Error();
				System.out.println("Solution status = " + cplex.getStatus());
				System.out.println("Solution value  = " + cplex.getObjValue());

				Set<Allocation>s=
						processCplexResults(cplex, varPerAlloc);
				
				

				//s.sort((x,y)->roles.indexOf(x.role) - roles.indexOf(y.role));
				System.out.println("Minimizing the least happy one");
				printOutput(s,prefsPerAllocation);
				cplex.end();
				
				return s.stream().map(x->prefsPerAllocation.get(x)).
						max(Integer::compare).get();

			}
			catch (IloException e) {
				System.err.println("Concert exception caught '" + e + "' caught");
				throw new Error();
			}
		}
	}*/

	private static void printOutput(Set<Allocation> s, Map<Allocation, Integer> prefsPerAllocation) {
		/*for(Allocation a:s)
			System.out.println(a+" with preference rank:"+(prefsPerAllocation.get(a)));*/
		
		System.out.println("\n\nGeneral stats:");
		int maxUnhapiness = prefsPerAllocation.keySet().stream()
				.filter(x->s.contains(x))
				.map(x->prefsPerAllocation.get(x)).max(Integer::compareTo).get();
		for(int i = 0; i <= maxUnhapiness; i++)
		{
			final int count = i;
			System.out.println("# of rank "+(i)+"->"+s.stream().filter(x->prefsPerAllocation.get(x).equals(count)).count());
		}
		
	}

	private static Set<Allocation> processCplexResults(IloCplex cplex, Map<Allocation, IloNumVar> varPerAlloc) {
		return varPerAlloc.keySet().stream()
		.filter(x->{
			try {
				return cplex.getValue(varPerAlloc.get(x))>0.01;
			} catch (IloException e) {
				e.printStackTrace();
				throw new Error();

			}
		})
		.collect(Collectors.toSet());
	}

	private static Map<Allocation, IloNumVar> generateVariablePerAllocation(
			IloCplex cplex,
			Collection<Allocation> consideredAllocations
			) throws IloException {
		Map<Allocation, IloNumVar> varPerAlloc = new HashMap<>();

		for(Allocation pl : consideredAllocations)
				varPerAlloc.put(pl,
						cplex.boolVar(
						//cplex.numVar(0, 1,
								pl.toString()));
		
		return varPerAlloc;
	}

	private static void generateAllocationConstraints(IloCplex cplex, 
			Collection<Allocation> allocations, 
			Map<Allocation, IloNumVar> varPerAlloc) 
					throws IloException{
		
		for(String pl: allocations.stream()
				.map(x->x.receiver)
				.collect(Collectors.toSet()))
		{
			IloNumExpr oneRolePerPlayer = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.receiver.equals(pl))
					.map(x->x.resource)
					.collect(Collectors.toList()))
			{
				oneRolePerPlayer = cplex.sum(
						oneRolePerPlayer, varPerAlloc.get(new Allocation(pl, s)));
			}
			cplex.addEq(1, oneRolePerPlayer, "EachReiverIsGivenExactlyOneItem("+pl+")");
		}

		for(String ro: allocations.stream()
				.map(x->x.resource)
				.collect(Collectors.toSet()))
		{
			IloNumExpr onePlayerPerRole = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.resource.equals(ro))
					.map(x->x.receiver)
					.collect(Collectors.toList()))
			{
				onePlayerPerRole = cplex.sum(
						onePlayerPerRole, varPerAlloc.get(new Allocation(s, ro)));
			}
			cplex.addGe(1, onePlayerPerRole, "EachItemIsGivenAtMostOnce("+ro+")");
		}
	}

	private static Map<Allocation, Integer> initPrefs(String file) {
		Map<Allocation, Integer>baseValues = new HashMap<>();

		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				baseValues.put(new Allocation(split[1], split[0]), Integer.parseInt(split[2]));
			}
			
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		
		return baseValues;
	}
	
	private static Map<Allocation,Integer> fromPosNegToPreferences(Map<Allocation, Integer> baseValues)
	{

		Set<String> requesters = 
				baseValues
				.keySet()
				.stream()
				.map(x->x.receiver)
				.collect(Collectors.toSet());
		
		Set<String> resources = 
				baseValues.keySet()
				.stream()
				.map(x->x.resource)
				.collect(Collectors.toSet());
		
		Map<Allocation, Integer> res = new HashMap<>();
		for(String s: requesters)
		{
			Set<Allocation> positiveValues = baseValues.keySet().stream()
					.filter(x->x.receiver.equals(s) && baseValues.get(x)> 0).collect(Collectors.toSet());
			
			//sort by decreasing order
			Set<Allocation> sortedPositiveValues = new TreeSet<>((x, y)-> {
				if(baseValues.get(x)==baseValues.get(y)) return x.toString().compareTo(y.toString());
				return baseValues.get(y)-baseValues.get(x);});
			
			sortedPositiveValues.addAll(positiveValues);
			
			Set<Allocation> negativeValues =  baseValues.keySet().stream()
					.filter(x->x.receiver.equals(s) && baseValues.get(x)< 0).collect(Collectors.toSet());
			Set<Allocation> sortedNegativeValues = new TreeSet<>(
					(x, y)-> {
						if(baseValues.get(x)==baseValues.get(y)) return x.toString().compareTo(y.toString());
						return baseValues.get(y)-baseValues.get(x);});
			sortedNegativeValues.addAll(negativeValues);
			
			Set<String> nonAllocatedValues = new HashSet<>();
			nonAllocatedValues.addAll(resources);
			nonAllocatedValues.removeAll(positiveValues.stream().map(x->x.resource).collect(Collectors.toList()));
			nonAllocatedValues.removeAll(negativeValues.stream().map(x->x.resource).collect(Collectors.toList()));
			
			int currentOrder = 0;
			int currentIndex = 0;
			Allocation prev = null;
			for(Allocation a:sortedPositiveValues)
			{
				if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
					currentOrder = currentIndex;
				prev = a;
				currentIndex++;
				
				res.put(a, currentOrder);
			}
			
			currentOrder = currentIndex;
			for(String allocatee:nonAllocatedValues)
			{
				currentIndex++;
				res.put(new Allocation(s, allocatee), currentOrder);
			}
			currentOrder = currentIndex;
			for(Allocation a:sortedNegativeValues)
			{
				if(prev != null && !baseValues.get(a).equals(baseValues.get(prev)))
					currentOrder = currentIndex;
				prev = a;
				currentIndex++;
				
				res.put(a, currentOrder);
			}

		}
		return res;
	}

}