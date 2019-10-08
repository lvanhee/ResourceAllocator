package output;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import input.InputBuilder;
import input.InputBuilder.ParameterTypes;
import input.ProblemInstance.OutputType;
import input.ProblemInstance;
import model.ResourceType;
import model.User;
import model.UserResourceTypeAllocation;
import solver.ResourceInstance;
import solver.UserResourceInstanceAllocation;
import model.UserGroup;

public class Printer {

	public static String getHappinessStatistics(
			Set<UserResourceInstanceAllocation> s, 
			Map<UserResourceInstanceAllocation, Integer> prefsPerAllocation,
			OutputType ot
			) {
		/*for(Allocation a:s)
			System.out.println(a+" with preference rank:"+(prefsPerAllocation.get(a)));*/
		
		String res = "";
		if(ot.equals(OutputType.LATEX_REPORT))
		{
			res+="\\subsection{Happiness Statistics} \n";
			res+="\\subsubsection{General Statistics} \n";
		}
		else if(ot.equals(OutputType.CONSOLE_PRINT)) res +="\n\nGeneral stats:";
		else throw new Error();
		
		int maxUnhapiness = prefsPerAllocation.keySet().stream()
				.filter(x->s.contains(x))
				.map(x->prefsPerAllocation.get(x)).max(Integer::compareTo).get();
				
		if(ot.equals(OutputType.LATEX_REPORT))
		{
			
			res +="\n Number of individuals per level of satisfaction\\\\" + 
					"\\begin{center}" + 
					"\\begin{tabular}{|c|c|}\n" + 
					"	\\hline\n" + 
					"	Level of satisfaction & Number of individuals\\\\\n" + 
					"	\\hline";
		}
		
		for(int i = 0; i <= maxUnhapiness; i++)
		{
			final int count = i;
			long nbOfThatHappiness=
					s.stream().filter(x->prefsPerAllocation.get(x).equals(count)).count();
			if(ot.equals(OutputType.LATEX_REPORT))
				res+=i+"&"+nbOfThatHappiness+"\\\\\n";
			else if(ot.equals(OutputType.CONSOLE_PRINT))
				res+="# of rank "+(i)+"->"+nbOfThatHappiness;
			else throw new Error();
		}

		if(ot.equals(OutputType.LATEX_REPORT))
			res+="	\\hline\n" + 
					"\\end{tabular}\n" + 
					"\\end{center}";
		
		
		
		
		return res;
	}
	
	public static void processResults(Set<UserResourceInstanceAllocation>userResourceAllocations, 
		ProblemInstance inF
			) {
		List<UserResourceInstanceAllocation> sortedList = getSortedUserResourceAllocations(userResourceAllocations);
		
		for(UserResourceInstanceAllocation a:sortedList)
			System.out.println(a+" rank:"+
		inF.getAllocationsPerResourceInstance().get(a));
		
		System.out.println("\n\n");
		
		System.out.println(getUserPerResourceLatex(inF, userResourceAllocations));
	
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

	private static Set<ResourceInstance> getAllocatedResources(
			Set<UserResourceInstanceAllocation>userResourceAllocations) {
		return 
		userResourceAllocations.stream()
		.map(x->x.getResource())
		.collect(Collectors.toSet());
	}

	private static Map<ResourceInstance, Set<User>> getUsersPerResource(Set<UserResourceInstanceAllocation>userResourceAllocations) {
		return getAllocatedResources(userResourceAllocations).
		stream().
		collect(
				Collectors.toMap(Function.identity(), 
				x-> {
					SortedSet<User>treeSet = new TreeSet<>((z,y)->z.toString().compareTo(y.toString()));
					treeSet.addAll(
							userResourceAllocations.stream()
							.filter(y-> y.getResource().equals(x))
							.map(y -> y.getUser())
							.collect(Collectors.toSet()));
					return treeSet;
				}));
	}

	private static Map<UserGroup, Integer> getSatisfactionPerGroup(
			ResourceInstance s, 
			Set<UserResourceInstanceAllocation>userResourceAllocations, ProblemInstance inF) {
		Map<User,Integer> satisfactionPerUser = 
				getUsersPerResource(userResourceAllocations)
				.get(s)
				.stream()
				.collect(
						Collectors.toMap(
								Function.identity(),
								x->
								inF.
								getAllocationsPerResourceInstance().get(
										UserResourceInstanceAllocation.newInstance(x,s))
								)
						);

		Set<UserGroup>involvedGroups = 
				satisfactionPerUser.keySet().stream()
				.map(x->inF.getUserGroupOf(x))
				.collect(Collectors.toSet());
		
		return involvedGroups.stream()
				.collect(
						Collectors.toMap(Function.identity(), 
								x->
						satisfactionPerUser.get(x.getUsers().iterator().next())));
	}

	private static List<UserResourceInstanceAllocation> getSortedUserResourceAllocations(Set<UserResourceInstanceAllocation> res) {
		List<UserResourceInstanceAllocation> sortedList = new LinkedList<>();
		
		sortedList.addAll(res);
		Collections.sort(sortedList, (x,y)-> x.getUser().toString()
				.compareTo(y.getUser().toString()));
		
		return sortedList;
	}

	public static void displayResults(
			String[] input,
			ProblemInstance inF, 
			Set<UserResourceInstanceAllocation> res) {
		if(inF.getOutputType().equals(ProblemInstance.OutputType.LATEX_REPORT))
		{
			printLatexReport(input, inF, res );
			return;
		}
		

	}

	private static Map<ResourceType, List<Long>> getNumberOfDesirePerPreferenceLevelPerProject(ProblemInstance inF) {
		Map<ResourceType, List<Long>> numberOfDesirePerPreferenceLevelPerProject = 
				inF.getResourceTypes()
				.stream()
				.collect(
						Collectors.toMap(x->x, 
								x->new LinkedList()));

		for(int i = 0 ; i < inF.getResourceTypes().size();i++)
			for(ResourceType resource:inF.getResourceTypes())
			{
				final int temp = i;
				long count = inF.getAllocationsPerResourceType()
						.keySet()
						.stream()
						.filter(x->x.getResource().equals(resource))
						.filter(x->inF.getAllocationsPerResourceType().get(x)<=temp)
						.count();

				/*	System.out.println(exhaustivePrefsPerAllocation
		.keySet()
		.stream()
		.filter(x->x.resource.equals(resource))
		.filter(x->exhaustivePrefsPerAllocation.get(x)<=temp)
		.collect(Collectors.toSet()));*/
				numberOfDesirePerPreferenceLevelPerProject.get(resource).add(count);
			}
		return numberOfDesirePerPreferenceLevelPerProject;
	}

	private static void printLatexReport(String[] input, ProblemInstance inF, Set<UserResourceInstanceAllocation> allocations) {
		
		String res = getPreamble();
		
		res+=getInputSection(input);
		
		res+=getResultSection(inF, allocations);
		
		res+=getStatisticsSection(inF, allocations);
		
		
		res+=showGraph(inF, allocations);
		
		
		res+="\n \\end{document} \n";
	    try {
			Files.write(Paths.get("report.tex"), res.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new Error();
		}
	}

	private static String showGraph(ProblemInstance inF, Set<UserResourceInstanceAllocation> allocations) {
		String res ="";
		//res+="\\section{Allocation Graphs}\n";
		
		res+="\\begin{center}\n";
		int worseInsatisfaction = getWorseInsatisfaction(inF, allocations);
		for(int i = 0; i < worseInsatisfaction +1 ; i++)
		{
			res+="\\newpage\\textbf{Allocations preference rank} "+i;
			Map<User, Set<ResourceType>> m = new HashMap<User, Set<ResourceType>>();

			res +=" \\digraph[height=0.65\\paperheight]{alloc"+i+"}{ rankdir=LR;ranksep=25;\n";
			Set<UserResourceInstanceAllocation> allocs = inF.getAllocationsFilteredBy(i);
			
			for(User u: allocations.stream()
					.filter(x->allocs.contains(x))
					.map(x->x.getUser()).collect(Collectors.toSet()))
			{
				res+=u.toString()+"[shape=diamond, penwidth=3]\n";
			}
			
			for(UserResourceInstanceAllocation al:allocs)
			{
				int insat = inF.getInsatisfactionFor(al);
				float tint = ((float)insat)/(worseInsatisfaction +1);
				String colorString = "\""+tint+" "+tint+" "+tint+"\"";  
				
				boolean actualMatch = (
						allocations.stream().anyMatch(
						x->x.getResource().getResourceType().equals(al.getResource().getResourceType())&&
						x.getUser().equals(al.getUser())
						));
					
				if(!m.containsKey(al.getUser()))
					m.put(al.getUser(), new HashSet<ResourceType>());
				if(!m.get(al.getUser()).contains(al.getResource().getResourceType()))
					{
					String thickness="";
					if(actualMatch)
						thickness=",penwidth=3";
					res+=al.getUser()+"->"+al.getResource().getResourceType()
							+"[color="+colorString+thickness+"]"
							+"\n";
					m.get(al.getUser()).add(al.getResource().getResourceType());
					}
			}
		
	
		res+="}\n  ";
		}
		res+="\n\\end{center}\n";

		return res;
		
		
	}

	private static String getStatisticsSection(ProblemInstance inF, 
			Set<UserResourceInstanceAllocation> allocations) {
		String res = "\\section{General Statistics}\n ";
		res+=getHappinessStatistics(allocations, inF.getAllocationsPerResourceInstance(), inF.getOutputType());
		
		res += "\\subsection{Degree of Interest per Resource}\n ";
		res+=getHypeSection(inF, allocations);
		return res;
	}

	private static String getHypeSection(ProblemInstance inF, 
			Set<UserResourceInstanceAllocation> allocations) {
		int worseInsatisfaction = getWorseInsatisfaction(inF, allocations);
		
		
		String res =  "\\subsubsection{User Per Resource}"
				+"		\\begin{tikzpicture}[scale=0.95]\n" + 
				"		\\begin{axis}[\n" + 
				"		title={Number of interested users per resource},\n" + 
				"		xlabel={Cumulative desirability of the resource},\n" + 
				"		ylabel={Number of interested users for this resource},\n" + 
				"		cycle multi list={black, red,  blue, brown, cyan, magenta,"
				+ "  orange,violet, purple, gray, lightgray,darkgray,yellow,green \\nextlist solid,"
				+ "{dotted,mark options={solid}} \\nextlist	mark=*,mark=x,mark=o, mark=+, mark=-, mark=|},"
				+ "		legend style={font=\\fontsize{8}{5}\\selectfont, anchor=north west, column sep=1ex},"
				+"		]\n" ;
		/*
		
		
		if(!inF.getOutputType().equals(OutputType.LATEX_REPORT)) 
			throw new Error();
		System.out.println("Statistics for the current instance");
		System.out.println("Cumulative ranking per resource:"
				+ "(the kth value is for resource r is the number of users that "
				+ "rank r with value k or below)");
		
		System.out.println("Resources with high values for lower index are on high demand and thus likely to cause regret");
		*/
		
		Map<ResourceType, List<Long>> numberOfDesirePerPreferenceLevelPerProject = 
				getNumberOfDesirePerPreferenceLevelPerProject(inF);
		
		List<ResourceType> sortedResourceList = new LinkedList<>();
		sortedResourceList.addAll(inF.getResourceTypes());
		Collections.sort(sortedResourceList, (x,y)->x.toString().compareTo(y.toString()));

		for(ResourceType resource: sortedResourceList)
		{
			String plot ="		\\addplot\n" + 
					//"		color=p,\n" + 
					//"		mark=otimes,\n" + 
					"		coordinates{";

			for(int i = 0 ; i <= worseInsatisfaction+1; i++)
				plot+="("+i+","
						+numberOfDesirePerPreferenceLevelPerProject.get(resource).get(i)
						+")\t";

			plot+="};\n" + 
					"		\\addlegendentry{"+resource+"}";
			res+=plot+"\n";
		}

		res+="		\\end{axis}\n" + 
				"		\\end{tikzpicture}\n";

		
		res+="Number of users with at least the k$^{th}$ level of desirability:\\newline\n";
		for(ResourceType resource: sortedResourceList)
		{
			res+="\\mbox{Resource "+resource+" ";
			for(int i = 0 ; i <= worseInsatisfaction+1; i++)
			{
				res+="\\quad "+i+":"+numberOfDesirePerPreferenceLevelPerProject.get(resource).get(i);
			}
			
			res+="}\n";
		}
		res+="\\subsubsection{Hype Factors}";
		long maxHype = 
				(numberOfDesirePerPreferenceLevelPerProject.values()
				.stream().map(x->x.get(0)).max((x,y)->
				Integer.max(x.intValue(), y.intValue())).get());
		for(int i = 0; i < (int)maxHype+1;i++) {
			final int index = i;
			long numberOfProjectsWithThisHype = 
					numberOfDesirePerPreferenceLevelPerProject.values()
					.stream()
					.map(x->x.get(0))
					.filter(x->x.intValue()==index)
					.count();
			if(numberOfProjectsWithThisHype != 0)
				res+=numberOfProjectsWithThisHype+" resources are the best match for "+i+" users.\\newline";
		}
		
		res+="\\subsubsection{Flexibility Factors}";

		for(int i = 0; i < (int)worseInsatisfaction+1;i++) {
			final int index = i;
			long sumInterestAtThisLevel = 
					numberOfDesirePerPreferenceLevelPerProject.values().stream()
					.map(x->x.get(index))
					.reduce(0l, (x,y)->x+y);
			long extraFlexibilityChoices = sumInterestAtThisLevel-(i+1)*inF.getAllUsers().size();
			res+=extraFlexibilityChoices+" flexibility choice were offered for desirability "+i+"\\\\";
		}


		return res;
	}

	private static int getWorseInsatisfaction(ProblemInstance inF,
			Set<UserResourceInstanceAllocation> allocations) {
		return
				allocations.stream().map(x->inF.getInsatisfactionFor(x))
				.collect(Collectors.toSet())
				.stream().max(Integer::compare).get();
	}

	private static String getResultSection(
			ProblemInstance inF,
			Set<UserResourceInstanceAllocation> res2) {

		String res = "\\section{Results}\n ";
		
		res+=
		"\\subsection{Resource Per User}\n "+
				"\n";
		res+= getResourcePerUserLatex(inF, res2);
		
		res+=
		"\\subsection{User per Resource}\n "+
				"\n";
		res+= getUserPerResourceLatex(inF, res2);
		
		res+=
		"\\subsection{Detailed report}\n "+
				"\n";
		res+= getDetailedReport(inF, res2);
		
		return res;
		
	}

	private static String getDetailedReport(ProblemInstance inF, 
			Set<UserResourceInstanceAllocation> allocationsOfResourceInstancesToUsers) {
		String res = "";

		Map<ResourceInstance, Set<User>> usersPerResources =
				getUsersPerResource(allocationsOfResourceInstancesToUsers);

		SortedSet<ResourceInstance> setOfResources = new TreeSet<ResourceInstance>(
				(x,y)->
				x.toString().compareTo(y.toString()));
		setOfResources.addAll(usersPerResources.keySet());

		for(ResourceInstance s: setOfResources)
		{
			Map<UserGroup, Integer> satisfactionPerGroup = 
					getSatisfactionPerGroup(s, allocationsOfResourceInstancesToUsers, inF);

			if(inF.getOutputType().equals(OutputType.LATEX_REPORT))
			{
				
				res+="Resource "+s.getResourceType()+", instance \\#"+s.getInstanceNumber()
				+" is allocated to:\\newline \n";
				
				for(UserGroup ug: satisfactionPerGroup.keySet())
					res+="---the group "+ug.getUsers()
					+", satisfaction of "+satisfactionPerGroup.get(ug)+"\\newline";
				
				res = res.substring(0, res.lastIndexOf("\\newline"))+".\\\\\n";
			}
			else if(inF.getOutputType().equals(OutputType.CONSOLE_PRINT))
				System.out.println(s+"->"+satisfactionPerGroup+
						" "+inF.getOwner(s));
			else throw new Error();
		}

		return res;
	}

	private static String getUserPerResourceLatex(
			ProblemInstance inF, 
			Set<UserResourceInstanceAllocation> allocationsOfResourceInstancesToUsers) {
		String res = "";
		
		Map<ResourceInstance, Set<User>> usersPerResources =
				getUsersPerResource(allocationsOfResourceInstancesToUsers);
		
				
		SortedSet<ResourceInstance> setOfResources = new TreeSet<ResourceInstance>(
				(x,y)->
				x.toString().compareTo(y.toString()));
		
		setOfResources.addAll(usersPerResources.keySet());
		
		for(ResourceInstance s: setOfResources)
		{
			Map<UserGroup, Integer> satisfactionPerGroup = 
					getSatisfactionPerGroup(s, allocationsOfResourceInstancesToUsers, inF);
					
			if(inF.getOutputType().equals(OutputType.LATEX_REPORT))
			{
				Set<User> users = 
						satisfactionPerGroup.keySet().stream()
						.map(x->x.getUsers())
						.reduce(new HashSet<User>(), (x,y)->{x.addAll(y); return x;});
				res +=
						(toLatexString(s.toString())+toLatexString(users.toString())).replaceAll(" ", "")+"\\quad\n";
			}
			else if(inF.getOutputType().equals(OutputType.CONSOLE_PRINT))
				System.out.println(s+"->"+satisfactionPerGroup+
					" "+inF.getOwner(s));
			else throw new Error();
		}
		
		return res;
	}

	private static String getResourcePerUserLatex(ProblemInstance inF, Set<UserResourceInstanceAllocation> res2) {
		String res = "";
		for(UserResourceInstanceAllocation s: getSortedUserResourceAllocations(res2))
		{
			res += toLatexString(s.toString())+"\\quad\n";
		}
		return res;
	}

	private static String getInputSection(String[] input) {
		InputBuilder ib = InputBuilder.parse(input);
		
		String res = "\\section{Input}\n" + 
				"\n";
		
		for(ParameterTypes pt: ib.getParameters())
		{
			res+=ParameterTypes.toLatexString(pt)+" = "+toLatexString(ib.get(pt))+"\\newline\n";
		}
		return res;
	}

	private static String getPreamble() {
		return "\\documentclass{article}\n" + 
				"\\usepackage{pgfplots}\n" 
				+"\\usepackage[pdf]{graphviz}"

				+"\\pgfplotsset{width=1.1\\linewidth,compat=1.9}\n" + 
				"\n" + 
				"\\title{Allocation report}\n" + 
				"\\begin{document}\n" + 
				"\\maketitle\n"
				+"\\sloppy"
				+"\n";
	}

	public static String toLatexString(String string) {
		return string.replaceAll("_", "\\\\_").replaceAll("#", "\\\\#");
	}

}
