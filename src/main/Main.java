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
import input.InputFormat;
import model.Resource;
import model.ResourceProvider;
import model.User;
import model.UserAllocation;
import model.UserGroup;
import solver.SatisfactionMeasure;
import solver.Solver;

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
		Optional<String> resourceProviders = Optional.empty(); 
		if(args.length>2)
			resourceProviders = Optional.of(args[3]);
		
		InputFormat input = Main.initPrefs(fileName, minSizeProject, maxSizeProject, resourceProviders);
		
		Main.displayStats(input);
		
		Set<UserAllocation> res = Solver.optimize(input);
		
		printOutput(res, input.getExhaustiveAllocations());
	
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

	public static void processResults(Set<UserAllocation>res, 
		InputFormat inF
			) {
		List<UserAllocation> sortedList = new LinkedList<>();
		sortedList.addAll(res);
		Collections.sort(sortedList, (x,y)-> x.getUser().toString()
				.compareTo(y.getUser().toString()));
		
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
					SortedSet<User>treeSet = new TreeSet<>((z,y)->z.toString().compareTo(y.toString()));
					treeSet.addAll(
							sortedList.stream()
							.filter(y-> y.getResource().equals(x))
							.map(y -> y.getUser())
							.collect(Collectors.toSet()));
					return treeSet;
				}));

		SortedSet<Resource> setOfResources = new TreeSet<Resource>(
				(x,y)->
				x.toString().compareTo(y.toString()));
		setOfResources.addAll(usersPerResources.keySet());
		for(Resource s: setOfResources)
		{
			Map<User,Integer> satisfactionPerUser = 
					usersPerResources.get(s)
					.stream()
					.collect(
							Collectors.toMap(
									Function.identity(),
									x->inF.
									getExhaustiveAllocations().get(UserAllocation.newInstance(x,s)))
							);
			System.out.println(s+"->"+satisfactionPerUser+
					" "+inF.getOwner(s));
		}

		/*System.out.println("Allocation per group:");
		for(UserGroup ug: inF.getUserGroups())
			{
			System.out.println(
					res.stream()
					.filter(x-> ug.getUsers().contains(x))
					.collect(Collectors.toSet())
					);
			}*/
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

		System.out.print("R\t");
		for(int i = 0 ; i < sortedResourceList.size(); i++)
		System.out.print(i+"\t");
		
		System.out.println();
		
		 int maxNameLength = inF.getAllResourceOwners()
		 .stream()
		 .map(x->x.getName().length())
		 .max(Integer::max).get();
		
		for(Resource resource: sortedResourceList)
		{
			System.out.println("R:"+resource+","+
		inF.getOwner(resource)+"\t"+
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
			Map<UserAllocation, IloIntVar> varPerAlloc
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

	

	private static InputFormat initPrefs(String file, int minsize, int maxsize, Optional<String> resourceProviders)
	{
		Map<UserAllocation, Double>absoluteValuePerAllocation = new HashMap<>();
		Map<User, UserGroup> groupsPerUsers = new HashMap<>();
		
		Map<Resource, ResourceProvider> providerPerResource = new HashMap<>();
			
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
		
		if(resourceProviders.isPresent())
			try {
				for(String s: Files.readAllLines(Paths.get(resourceProviders.get())))
				{
					String[] split = s.split(";");
					ResourceProvider rp = ResourceProvider.newInstance(split[0]);
					for(int i = 1 ; i < split.length; i++)
						providerPerResource.put(
								Resource.newInstance(split[i]),rp);
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new Error();
			}
		
		InputFormat res = InputFormat.newInstance(
				absoluteValuePerAllocation, 
				minsize,
				maxsize,
				userGroups, 
				providerPerResource);
		
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