\ENCODING=ISO-8859-1
\Problem name: ilog.cplex

Minimize
 obj: 3 student0.project1#0 + 2 student0.project0#1 + 3 student.project0#2
      + 2 student.project1#3 + 3 student.project2#4 + 3 student.project3#5
      + 3 student.project4#6
Subject To
 _EachReiverIsGivenExactlyOneItem(student0)#0: student0.project1#0
                                 + student0.project0#1  = 1
 _EachReiverIsGivenExactlyOneItem(student)#1: student.project0#2
                                 + student.project1#3 + student.project2#4
                                 + student.project3#5 + student.project4#6  = 1
 _EachItemIsGivenAtMostOnce(project2)#2: student.project2#4 <= 1
 _EachItemIsGivenAtMostOnce(project1)#3: student0.project1#0
                                 + student.project1#3 <= 1
 _EachItemIsGivenAtMostOnce(project4)#4: student.project4#6 <= 1
 _EachItemIsGivenAtMostOnce(project3)#5: student.project3#5 <= 1
 _EachItemIsGivenAtMostOnce(project0)#6: student0.project0#1
                                 + student.project0#2 <= 1
Bounds
 0 <= student0.project1#0 <= 1
 0 <= student0.project0#1 <= 1
 0 <= student.project0#2 <= 1
 0 <= student.project1#3 <= 1
 0 <= student.project2#4 <= 1
 0 <= student.project3#5 <= 1
 0 <= student.project4#6 <= 1
Binaries
 student0.project1#0  student0.project0#1  student.project0#2 
 student.project1#3  student.project2#4  student.project3#5 
 student.project4#6 
End
