import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.event.ListSelectionEvent;

import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloIntVar;
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
	
	private static final class InputFormat
	{
		private final Map<Allocation, Double> rawPreferencePerAllocation;
		private final Map<String, String> pairsOfUsers;
		private final int minNbUsersPerResource;
		private final int maxNbUsersPerResource;
		
		private final Map<Allocation, Integer> exhaustivePrefsPerAllocation;
		
		private InputFormat(
				Map<Allocation, Double> baseValues,
				Map<String, String> usersPairs,
				int numberOfUsersPerResource,
				int maxNbUsersPerResource
				) {
			this.rawPreferencePerAllocation = baseValues;
			this.pairsOfUsers = usersPairs;
			this.minNbUsersPerResource = numberOfUsersPerResource;
			this.maxNbUsersPerResource = maxNbUsersPerResource;
			 exhaustivePrefsPerAllocation = 
						fromPosNegToPreferences(rawPreferencePerAllocation);
		}
		public Map<Allocation, Double> getRawAllocations() {
			return rawPreferencePerAllocation;
		}
		public int getMaxNbUsersPerResource() {
			return maxNbUsersPerResource;
		}
		public int getMinNumUsersPerResource() {
			return minNbUsersPerResource;
		}
		public Set<String> getResources() {
			return 
					exhaustivePrefsPerAllocation.keySet()
					.stream()
					.map(x->x.resource)
					.collect(Collectors.toSet());
		}
		public Map<Allocation, Integer> getExhaustiveAllocations() {
			return exhaustivePrefsPerAllocation;
		}
		
		//not implemented
		public Set<Allocation> getHardConstraints() {
			return new HashSet();
		}
	}

	private static final String NOBODY_SYMBOL = "aucun";

	/**
	 * -ea -Djava.library.path=/export/home/vanhee/Documents/software/cplex/cplex_studio/cplex/bin/x86-64_linux
	 * 
	 * Two file formats, based on CSV are accepted. 
	 * The first is for decoupled resources and users. The file format is:
	 * resource;user;preference
	 * where resource and user are free strings, without ";" and preference is an integer
	 * Such a line means "the user grades this resource with the given preference".
	 * 
	 * The second file format, applied for users that can operate in pairs is:
	 * resource;user;user;preference
	 * where "aucun" means "single user to be paired with someone else"
	 * 
	 * In that case, we assume that 
	 * -each resource can be acquired by two users
	 * -each resource must be either acquired by zero or two users
	 * -pairs cannot be split
	 * -single users must be paired together
	 * 
	 * 
	 * @param args
	 */
	public static void main(String[] args)
	{
		
		if(args.length < 1)
		{
			System.out.println("Please provide the interest input file");
			throw new Error();
		}
		
		InputFormat input = initPrefs(args[0]);
		
		displayStats(input);

		Optional<Set<Allocation>> res = Optional.empty();
		int worseCase = 0;
		while(!res.isPresent())
		{
			worseCase ++;
			System.out.println("Trying to find an allocation with least satisfaction rank of:"+worseCase);
			

			res= optimizeAccordingToMaxInsatisfaction(
					worseCase,
					input);
		}
		
		processResults(res.get(), input);

		
			
			
	}

	private static void processResults(Set<Allocation>res, 
		InputFormat inF
			) {
		List<Allocation> sortedList = new LinkedList<>();
		sortedList.addAll(res);
		sortedList.sort((x,y)-> x.receiver.compareTo(y.receiver));
		
		for(Allocation a:sortedList)
			System.out.println(a+" rank:"+
		inF.getExhaustiveAllocations().get(a));
		
		System.out.println("\n\n");
		
		Set<String> allocatedResources = 
				sortedList.stream()
				.map(x->x.resource)
				.collect(Collectors.toSet());
		
		Map<String, Set<String>> usersPerResources =
		allocatedResources.
		stream().
		collect(Collectors.toMap(Function.identity(), 
				x-> {
					return
		sortedList.stream()
		.filter(y-> y.resource.equals(x))
		.map(y -> y.receiver)
		.collect(Collectors.toSet());
				}));
		
		for(String s: usersPerResources.keySet())
			System.out.println(s+"->"+usersPerResources.get(s));
	}

	private static void displayStats(InputFormat inF) {
		
		
		
		System.out.println("Cumulative ranking per resource:"
				+ "(the third number corresponds to the number of users that "
				+ "rank this resource within its top three)");
		
		System.out.println("Resources with high values for lower index are on high demand and thus likely to cause regret");
		
		Map<String, List<Long>> rankPerProject = 
				inF.getResources()
				.stream()
				.collect(
						Collectors.toMap(x->x, 
								x->new LinkedList()));
		
		for(int i = 0 ; i < inF.getResources().size();i++)
			for(String resource:inF.getResources())
			{
				final int temp = i;
				long count = inF.getExhaustiveAllocations()
						.keySet()
						.stream()
						.filter(x->x.resource.equals(resource))
						.filter(x->inF.getExhaustiveAllocations().get(x)<=temp)
						.count();
				
			/*	System.out.println(exhaustivePrefsPerAllocation
				.keySet()
				.stream()
				.filter(x->x.resource.equals(resource))
				.filter(x->exhaustivePrefsPerAllocation.get(x)<=temp)
				.collect(Collectors.toSet()));*/
				rankPerProject.get(resource).add(count);
			}
		
		List<String> sortedResourceList = new LinkedList<>();
		sortedResourceList.addAll(inF.getResources());
		Collections.sort(sortedResourceList);
		for(String resource: sortedResourceList)
		{
			System.out.println("R:"+resource+"\t"+
		rankPerProject.get(resource).toString()
		.replaceAll("\\[", "")
		.replaceAll("\\]", "")
		.replaceAll(",", "\t"));
		}
	}

	private static Optional<Set<Allocation>> optimizeAccordingToMaxInsatisfaction(
			int maxInsatisfaction,
			InputFormat inF)
	{

		try {
			IloCplex cplex = new IloCplex();
			cplex.setOut(null);
			
			Set<Allocation> consideredAllocations = 
					inF.getExhaustiveAllocations().keySet().stream().filter(
							x->inF.getExhaustiveAllocations().get(x)<=maxInsatisfaction).collect(Collectors.toSet());

			SortedSet<String> users = new TreeSet<>();
			users.addAll(
					consideredAllocations.stream().map(x->x.receiver).collect(Collectors.toSet()));

			SortedSet<String> resources = new TreeSet<>();
			resources.addAll(
					consideredAllocations.stream().map(x->x.resource).collect(Collectors.toSet()));
			
			Map<Allocation, IloNumVar> allocToVar = 
					generateVariablePerAllocation(cplex,
					consideredAllocations
					);
			
			Map<String, IloIntVar> isAllocatedResourceVars = 
					resources.stream().collect(
							Collectors.toMap(Function.identity(),
									x->
									{
										try {
											return cplex.boolVar("isAllocated("+x+")"
													);
										} catch (IloException e) {
											e.printStackTrace();
											throw new Error();
										}
									}));
			
			Map<String, IloIntVar> resourceMinLiftingJokerVars = 
					resources.stream().collect(
							Collectors.toMap(Function.identity(),
									x->
									{
										try {
											return cplex
													.boolVar("jokerForDiscardingTheMinAllocationConstraint("+x+")"
													);
										} catch (IloException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
											throw new Error();
										}
									}));

			for(String pl:users)
			{
				int worseAllocValue = 1;
				if(consideredAllocations.stream()
						.anyMatch(x-> x.receiver.equals(pl)))
				{
					Allocation worseAlloc = consideredAllocations.stream()
							.filter(x-> x.receiver.equals(pl))
							.max((x,y)->inF.getExhaustiveAllocations().get(x)-
									inF.getExhaustiveAllocations().get(y)).get();
					worseAllocValue = inF.getExhaustiveAllocations().get(worseAlloc);
				}

				for(Allocation a: consideredAllocations)
					if(!consideredAllocations.contains(a) && a.receiver.equals(pl))
						inF.getExhaustiveAllocations().put(a, worseAllocValue+1);
			}
			

			
			cplex.addMinimize(
					getExpressionToMinimize(
							cplex, 
							consideredAllocations,
							users,
							inF.getExhaustiveAllocations(),
							allocToVar));
			
			generateAllocationConstraints(
					cplex,
					consideredAllocations,
					resources,
					allocToVar,
					isAllocatedResourceVars,
					resourceMinLiftingJokerVars,
					inF
					);
			
			generateHardAllocationsConstraints(cplex, allocToVar, inF.getHardConstraints());

			cplex.exportModel("mipex1.lp");

			if (! cplex.solve() )
				return Optional.empty();
			System.out.println("Solution status = " + cplex.getStatus());
			Set<Allocation>s=
					processCplexResults(cplex, allocToVar);
			
			

			//s.sort((x,y)->roles.indexOf(x.role) - roles.indexOf(y.role));
			System.out.println("Minimizing the number of least happy people:");
			printOutput(s,inF.getExhaustiveAllocations());
			cplex.end();
			
			return Optional.of(s);

		}
		catch (IloException e) {
			System.err.println("Concert exception caught '" + e + "' caught");
			throw new Error();
		}
	}

	private static IloNumExpr getExpressionToMinimize(
			IloCplex cplex, 
			Set<Allocation> allowedAllocations, SortedSet<String> users,
			Map<Allocation, Integer> prefsPerAllocation,
			Map<Allocation, IloNumVar> varPerAlloc
			) throws IloException {
		IloNumExpr exprToOptimize =  cplex.constant(0);
		for(Allocation a:allowedAllocations)
		{
			exprToOptimize = 
					cplex.sum(
							exprToOptimize,
							cplex.prod(
									Math.pow(users.size(), prefsPerAllocation.get(a))+1,
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
		return exprToOptimize;
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

	private static void generateAllocationConstraints(
			IloCplex cplex, 
			Set<Allocation> allocations, 
			SortedSet<String> resources,
			Map<Allocation, IloNumVar> varPerAlloc,
			Map<String, IloIntVar> allocatedResourceVar,
			Map<String, IloIntVar> allocationJoker,
			InputFormat inF
			) 
					throws IloException{
		
		for(String pl: allocations.stream()
				.map(x->x.receiver)
				.collect(Collectors.toSet()))
		{
			IloNumExpr oneResourcePerUser = 
					cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.receiver.equals(pl))
					.map(x->x.resource)
					.collect(Collectors.toList()))
			{
				oneResourcePerUser = 
						cplex.sum(
						oneResourcePerUser,
						varPerAlloc.get(new Allocation(pl, s)));
			}
			cplex.addEq(
					1,
					oneResourcePerUser,
					"EachUserIsGivenExactlyOneResource("+pl+")");
		}

		for(String resource: resources)
		{
			IloNumExpr maxKUsersPerResource = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.resource.equals(resource))
					.map(x->x.receiver)
					.collect(Collectors.toList()))
			{
				maxKUsersPerResource = cplex.sum(
						maxKUsersPerResource, varPerAlloc.get(new Allocation(s, resource)));
			}
			cplex.addGe(
					inF.getMaxNbUsersPerResource(), 
					maxKUsersPerResource, 
					"EachResourceIsAllocatedAtMostKTimes("+resource+","+
					inF.getMaxNbUsersPerResource()+")");
		}
		
		for(Allocation a:allocations)
		{
			cplex.addGe(
					allocatedResourceVar.get(a.resource),
					varPerAlloc.get(a),
					"CountsAsAllocatedIfAllocatedFor("+a.resource+","+a+")");
		}	
		
		for(String resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.resource.equals(resource))
					.map(x->x.receiver)
					.collect(Collectors.toList()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(new Allocation(s, resource)));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMaxNbUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addLe(
					countUsersPerResource,
					0, 
					"EachNonAllocatedResourceIsAllocatedAtMost0TimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		for(String resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.resource.equals(resource))
					.map(x->x.receiver)
					.collect(Collectors.toList()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(new Allocation(s, resource)));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
			
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(inF.getMinNumUsersPerResource(),
							allocationJoker.get(resource)));
					
			cplex.addGe(
					countUsersPerResource,
					0, 
					"EachAllocatedResourceMustBeAllocatedAtLeastKTimesUnlessTheJokerIsUsed("+resource+")");
		}
		
		
		IloNumExpr allJokers = cplex.constant(0);
		for(IloIntVar joker: allocationJoker.values())
			allJokers = cplex.sum(allJokers, joker);
		cplex.addLe(allJokers, 1,
				"AtMostOneJoker");
		
		for(String resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(String s: allocations
					.stream()
					.filter(x->x.resource.equals(resource))
					.map(x->x.receiver)
					.collect(Collectors.toList()))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(new Allocation(s, resource)));
			}
			
			//if the resource is not allocated, then the minimum is zero
			countUsersPerResource = cplex.sum(
					countUsersPerResource,
					cplex.prod(-inF.getMinNumUsersPerResource(),
							allocatedResourceVar.get(resource)));
					
			cplex.addGe(
					0, 
					countUsersPerResource, 
					"EachAllocatedResourceIsAllocatedAtLeastKTimes("
					+resource+","+inF.getMinNumUsersPerResource()+")");
		}
		
		


		//ensure pairs
		for(String user1: inF.pairsOfUsers.keySet())
			for(String resource: resources)
			{
				String user2 = inF.pairsOfUsers.get(user1);
				
				Allocation u1PicksR= new Allocation(user1, resource);
				Allocation u2PicksR= new Allocation(user2, resource);
				
				if(!varPerAlloc.containsKey(u1PicksR))continue;
				if(!varPerAlloc.containsKey(u2PicksR))throw new Error();
				
				IloNumExpr exprUser1PicksR =
						varPerAlloc.get(u1PicksR);
				
				IloNumExpr exprUser2PicksR =
						varPerAlloc.get(u2PicksR);
				
				cplex.addEq(exprUser1PicksR, 
						exprUser2PicksR,
						"PairedUsersMatchTheirDecisions("
						+user1+","
						+user2+","+
						resource+")");
			}
		
		
		//ensure adequate resource consumption
		/*for(String resource:resources)
		{
			IloIntVar usingRKTimes = cplex.boolVar();
			IloNumExpr total = cplex.prod(usingRKTimes, inF.numberOfUsersPerResource);
			for(Allocation a: 
				varPerAlloc.keySet().stream()
				.filter(x->x.resource.equals(resource))
				.collect(Collectors.toSet()))
			{
				total = cplex.sum(total, varPerAlloc.get(a));
			}
			cplex.addEq(total, inF.numberOfUsersPerResource, "FullAllocationOfResource("+resource+")");
		}*/
		
		
	}

	private static InputFormat initPrefs(String file) {
		Map<Allocation, Double>baseValues = new HashMap<>();
		
		Map<String, String> usersPairs = new HashMap<>();
		
		boolean byPair = false;
		
		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				Allocation newAlloc = new Allocation(split[1], split[0]);
				
				if(newAlloc.receiver.equals("030"))
					System.out.println();
				
				Optional<Allocation>secondAlloc = Optional.empty();
				if(split.length==4)
				{
					byPair = true;
					if(!split[2].equals(NOBODY_SYMBOL))
						secondAlloc=Optional.of(new Allocation(split[2], split[0]));
				}
				
				if(secondAlloc.isPresent() && newAlloc.equals(secondAlloc.get()))
					throw new IllegalArgumentException("A user is making a match to him/herself");
				
				
				Double associatedValue = 
						Double.parseDouble(split[split.length-1]);
				
				if(baseValues.containsKey(newAlloc))
					throw new IllegalArgumentException(
							"Each allocation should be associated a score once, but "
							+ "the allocation:"+newAlloc+" is assigned a score at least twice!");
				baseValues.put(newAlloc,associatedValue);
				
				if(secondAlloc.isPresent())
				{
					if(baseValues.containsKey(secondAlloc.get()))
						throw new IllegalArgumentException(
								"Each allocation should be associated a score once, but"
								+ "the allocation:"+newAlloc+" is assigned a score at least twice!");
					else baseValues.put(secondAlloc.get(),associatedValue);
					
					usersPairs.put(newAlloc.receiver, secondAlloc.get().receiver);
					usersPairs.put(secondAlloc.get().receiver,newAlloc.receiver);
				}
					
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		
		int nbUsersPerResource = 1;
		if(byPair)
			nbUsersPerResource = 2;
		
		InputFormat res = new InputFormat(baseValues, usersPairs, nbUsersPerResource,nbUsersPerResource);
		
		checkCoherenceOf(res);
		return res;
	}
	
	private static void checkCoherenceOf(InputFormat res) {
		Set<String> users = new HashSet();
		
		for(Allocation a: res.getExhaustiveAllocations().keySet())
		{

			users.add(a.receiver);
		}
		

		
		if(users.size() > res.getResources().size()*res.maxNbUsersPerResource) 
			throw new Error("Some requesters cannot be satisfied, not enough resources. #requesters="+users.size()
			+", #resources="+res.getResources().size()*res.minNbUsersPerResource+"(resources names:"+res.getResources().size()+" x userPerResource:"+res.maxNbUsersPerResource+")"
			+"\n" +users+"\n"+res.getResources());
	}

	private static Map<Allocation,Integer> fromPosNegToPreferences(
			Map<Allocation, Double> 
			baseValues)
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
					.filter(x->x.receiver.equals(s) && baseValues.get(x)> 0)
					.collect(Collectors.toSet());
			
			//sort by decreasing order
			Set<Allocation> sortedPositiveValues = new TreeSet<>((x, y)-> {
				if(baseValues.get(x).equals(baseValues.get(y)))
					return x.toString().compareTo(y.toString());
				return -Double.compare(baseValues.get(x), 
						baseValues.get(y));});
			
			sortedPositiveValues.addAll(positiveValues);
			
			
		/*	System.out.println(
					exhaustivePrefsPerAllocation
					.keySet()
					.stream()
					.filter(x->x.receiver.equals("21406184"))
					.map(x-> x+":"+exhaustivePrefsPerAllocation.get(x))
					.collect(Collectors.toList())
					);*/
			
			Set<Allocation> negativeValues =  baseValues.keySet().stream()
					.filter(x->x.receiver.equals(s) && baseValues.get(x)< 0).collect(Collectors.toSet());
			Set<Allocation> sortedNegativeValues = new TreeSet<>(
					(x, y)-> {
						if(baseValues.get(x).equals(baseValues.get(y))) return x.toString().compareTo(y.toString());
						return -Double.compare(baseValues.get(x), 
								baseValues.get(y));});
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