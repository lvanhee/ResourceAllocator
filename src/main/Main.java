package main;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.swing.event.ListSelectionEvent;

import ilog.concert.IloException;
import ilog.concert.IloIntExpr;
import ilog.concert.IloIntVar;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import input.InputFormat;
import model.Resource;
import model.User;
import model.UserAllocation;
import model.UserGroup;
import solver.Solver;
import sun.usagetracker.UsageTrackerClient;

public class Main {
	private static final String NOBODY_SYMBOL = "aucun";
	

	/**
	 * -ea -Djava.library.path=/export/home/vanhee/Documents/software/cplex/cplex_studio/cplex/bin/x86-64_linux
	 * 
	 * Input:
	 * inputfile min_user_per_resource max_user_per_resource
	 * 
	 * Two file formats, based on CSV are accepted. 
	 * The first is for decoupled resources and users. The file format is:
	 * resource;user*;preference
	 * where user* is any sequence of user
	 * 'resource' and 'user' are free strings, without ";" 
	 * preference is an integer
	 * Such a line means "the user grades this resource with the given preference".
	 * Higher preference means more preferred/important
	 * 
	 * Groups can be filled in
	 * In that case, we assume that 
	 * -each resource can be acquired by two users
	 * -each resource must be either acquired by zero or two users
	 * -pairs cannot be split
	 * -single users must be paired together
	 *  
	 * @param args
	 */
	public static void main(String[] args)
	{
		Main.checkArgs(args);
		String fileName = args[0];
		int minSizeProject = Integer.parseInt(args[1]);
		int maxSizeProject = Integer.parseInt(args[2]);
		
		InputFormat input = Main.initPrefs(fileName, minSizeProject, maxSizeProject);
		
		Main.displayStats(input);
		
		Set<UserAllocation> res = Solver.optimize(input);
	
		Main.processResults(res, input);			
	}
	
	

	private static void checkArgs(String[] args) {
		if(args.length == 0)
		{
			System.out.println("Please provide the interest input file");
			throw new Error();
		}else if(args.length == 1)
		{
			System.out.println("Please provide the minimum size per group");
			throw new Error();
		}
		else if(args.length == 2)
		{
			System.out.println("Please provide the maximum size per group");
			throw new Error();
		}
	}

	private static void processResults(Set<UserAllocation>res, 
		InputFormat inF
			) {
		List<UserAllocation> sortedList = new LinkedList<>();
		sortedList.addAll(res);
		sortedList.sort((x,y)-> x.getUser().toString()
				.compareTo(x.getUser().toString()));
		
		for(UserAllocation a:sortedList)
			System.out.println(a+" rank:"+
		inF.getExhaustiveAllocations().get(a));
		
		System.out.println("\n\n");
		
		Set<Resource> allocatedResources = 
				sortedList.stream()
				.map(x->x.getResource())
				.collect(Collectors.toSet());
		
		Map<Resource, Set<User>> usersPerResources =
		allocatedResources.
		stream().
		collect(
				Collectors.toMap(Function.identity(), 
				x-> {
					return
							
							sortedList.stream()
							.filter(y-> y.getResource().equals(x))
							.map(y -> y.getUser())
							.collect(Collectors.toSet());
				}));
		
		for(Resource s: usersPerResources.keySet())
			System.out.println(s+"->"+usersPerResources.get(s));
		
		System.out.println("Allocation per group:");
		for(UserGroup ug: inF.getUserGroups())
			{
			System.out.println(
					res.stream()
					.filter(x-> ug.getUsers().contains(x))
					.collect(Collectors.toSet())
					);
			}
	}

	private static void displayStats(InputFormat inF) {
		System.out.println("Cumulative ranking per resource:"
				+ "(the kth value is for resource r is the number of users that "
				+ "rank r with value k or below)");
		
		System.out.println("Resources with high values for lower index are on high demand and thus likely to cause regret");
		
		Map<Resource, List<Long>> rankPerProject = 
				inF.getResources()
				.stream()
				.collect(
						Collectors.toMap(x->x, 
								x->new LinkedList()));
		
		
		
		for(int i = 0 ; i < inF.getResources().size();i++)
			for(Resource resource:inF.getResources())
			{
				final int temp = i;
				long count = inF.getExhaustiveAllocations()
						.keySet()
						.stream()
						.filter(x->x.getResource().equals(resource))
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
		
		List<Resource> sortedResourceList = new LinkedList<>();
		sortedResourceList.addAll(inF.getResources());
		Collections.sort(sortedResourceList, (x,y)->x.toString().compareTo(y.toString()));

		
		System.out.println();

		System.out.print("Resource\t");
		for(int i = 0 ; i < sortedResourceList.size(); i++)
		{
		
		System.out.print(i+"\t");
		}
		
		System.out.println();
		
		for(Resource resource: sortedResourceList)
		{
			System.out.println("R:"+resource+"\t"+
		rankPerProject.get(resource).toString()
		.replaceAll("\\[", "")
		.replaceAll("\\]", "")
		.replaceAll(",", "\t"));
		}
	}

	public static IloNumExpr getExpressionToMinimize(
			IloCplex cplex, 
			Set<UserAllocation> allowedAllocations, SortedSet<User> users,
			Map<UserAllocation, Integer> prefsPerAllocation,
			Map<UserAllocation, IloNumVar> varPerAlloc
			) throws IloException {
		IloNumExpr exprToOptimize =  cplex.constant(0);
		for(UserAllocation a:allowedAllocations)
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
		for(UserAllocation a: varPerAlloc.keySet())
		{
			weightedAllocations[i] = cplex.prod(
					varPerAlloc.get(a),
					prefsPerAllocation.get(a));
			i++;
		}
		return exprToOptimize;
	}

	public static void printOutput(Set<UserAllocation> s, Map<UserAllocation, Integer> prefsPerAllocation) {
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

	public static void generateAllocationConstraints(
			IloCplex cplex, 
			Set<UserAllocation> allocations, 
			SortedSet<Resource> resources,
			Map<UserAllocation, IloNumVar> varPerAlloc,
			Map<Resource, IloIntVar> allocatedResourceVar,
			Map<Resource, IloIntVar> allocationJoker,
			InputFormat inF
			) 
					throws IloException{
		
		Set<UserAllocation>validAllocations = varPerAlloc.keySet();
		for(User pl: inF.getAllUsers())
		{
			IloNumExpr oneResourcePerUser = 
					cplex.constant(0);
			for(UserAllocation al: inF.getAllAllocationsFor(pl))
			{
				if(!validAllocations.contains(al))continue;
				oneResourcePerUser = 
						cplex.sum(
						oneResourcePerUser,
						varPerAlloc.get(al));
			}
			System.out.println(oneResourcePerUser);
			cplex.addEq(
					1,
					oneResourcePerUser,
					"EachUserIsGivenExactlyOneResource("+pl+")");
		}

		for(Resource resource: resources)
		{
			IloNumExpr maxKUsersPerResource = cplex.constant(0);
			for(UserAllocation ua: inF.getAllocationsForResource(resource))
			{
				maxKUsersPerResource = cplex.sum(
						maxKUsersPerResource, varPerAlloc.get(ua));
			}
			System.out.println(maxKUsersPerResource);
			cplex.addGe(
					inF.getMaxNbUsersPerResource(), 
					maxKUsersPerResource, 
					"EachResourceIsAllocatedAtMostKTimes("+resource+","+
					inF.getMaxNbUsersPerResource()+")");
		}
		
		for(UserAllocation a:allocations)
		{
			cplex.addGe(
					allocatedResourceVar.get(a.getResource()),
					varPerAlloc.get(a),
					"CountsAsAllocatedIfAllocatedFor("+a.getResource()+","+a+")");
		}	
		
		for(Resource resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation s: inF.getAllocationsForResource(resource))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
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
		
		for(Resource resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation ua: inF.getAllocationsForResource(resource))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(ua));
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
		
		for(Resource resource: resources)
		{
			IloNumExpr countUsersPerResource = cplex.constant(0);
			for(UserAllocation s: inF.getAllocationsForResource(resource))
			{
				countUsersPerResource = cplex.sum(
						countUsersPerResource, varPerAlloc.get(s));
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
		for(UserGroup userGroup: inF.getUserGroups())
			for(Resource resource: resources)
			{
				User u0 = userGroup.getUsers().iterator().next();
				UserAllocation u0PicksR= UserAllocation.newInstance(u0, resource);
				
				if(!varPerAlloc.containsKey(u0PicksR)) continue;

				for(User u1: userGroup.getUsers())	
				{
					UserAllocation u1PicksR= UserAllocation.newInstance(u1, resource);

					IloNumExpr exprUser0PicksR =
							varPerAlloc.get(u0PicksR);

					IloNumExpr exprUser1PicksR =
							varPerAlloc.get(u1PicksR);

					cplex.addEq(exprUser0PicksR, 
							exprUser1PicksR,
							"PairedUsersMatchTheirDecisions("
									+u0+","
									+u1+","+
									resource+")");
				}
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

	private static InputFormat initPrefs(String file, int minsize, int maxsize)
	{
		Map<UserAllocation, Double>absoluteValuePerAllocation = new HashMap<>();
		Map<User, UserGroup> groupsPerUsers = new HashMap<>();
		
				
		try {
			for(String s: Files.readAllLines(Paths.get(file)))
			{
				String[] split = s.split(";");
				
				List<String> userNames = new ArrayList<>();
				userNames.addAll(Arrays.asList(split));
				userNames.remove(0);
				userNames.remove(userNames.size()-1);
				
				UserGroup usergroup = 
						UserGroup.newInstance(
						userNames
						.stream()
						.map(x-> User.newInstance(x))
						.collect(Collectors.toSet()));
				checkForInconsistencies(groupsPerUsers, usergroup);
				
				for(User u: usergroup.getUsers())
					groupsPerUsers.put(u, usergroup);

				Double associatedValue = 
						Double.parseDouble(split[split.length-1]);
				
				
				for(User u: usergroup.getUsers())
				{
					UserAllocation ua = UserAllocation.newInstance(u, 
							Resource.newInstance(split[0]));

					if(absoluteValuePerAllocation.containsKey(ua))
						throw new IllegalArgumentException(
								"Each allocation should be associated a score once, but "
										+ "the allocation:"+ua+" is assigned a score at least twice!");
					absoluteValuePerAllocation.put(ua,associatedValue);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new Error();
		}
		
		
		Set<UserGroup> userGroups = groupsPerUsers.values().stream().collect(Collectors.toSet());
		InputFormat res = InputFormat.newInstance(absoluteValuePerAllocation, minsize,maxsize, userGroups);
		
		checkCoherenceOf(res);
		return res;
	}
	
	private static void checkForInconsistencies(
			Map<User, UserGroup> groupsPerUsers,
			UserGroup usergroup) {
		boolean isPresent = groupsPerUsers
				.containsKey(usergroup.getUsers().iterator().next());
		
		if(isPresent)
		{
			for(User u: usergroup.getUsers())
				if(!groupsPerUsers.get(u).equals(usergroup))
					throw new Error(
							"User:"+u+" belongs to at least two groups: "+
					usergroup+" and "+groupsPerUsers.get(u));
		}
		else
			for(User u: usergroup.getUsers())
				if(groupsPerUsers.containsKey(u))
					throw new Error(
							"User:"+u+" belongs to at least two groups: "+
					usergroup+" and "+groupsPerUsers.get(u));
			
	}

	private static void checkCoherenceOf(InputFormat in) {
		if(in.getAllUsers().size() > in.getResources()
				.size()*in.getMaxNbUsersPerResource()) 
			throw new IllegalArgumentException("Not enough resources for satisfying all requesters. #requesters="+in.getAllUsers().size()
			+", #allocable_resources="+in.getResources().size()*in.getMinNumUsersPerResource()+" (#resources:"
					+in.getResources().size()+" x #userPerResource:"+in.getMaxNbUsersPerResource()+")"
			+"\nUsers:" +in.getAllUsers()+"\nResources:"+in.getResources());
	}

	

}