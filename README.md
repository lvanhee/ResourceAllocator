# ResourceAllocator
An exact tool for allocating resources to receivers that minimizes the worse insatisfaction of receivers

JVM initialization :
-ea -Djava.library.path=/path/to/cplex/cplex_studio/cplex/bin/x86-64_linux

Input :
file_of_preferences.txt

file format :
resourceX;receiverY;interest

where, for interest, the higher, the more preferred. Negative interests means "less than the unmentionned ones". Interests are relative preference orders. I.e. an interest of if p1 is associated a the highest interest of 100, it won't matter if it is associated an interest of 1000 instead.

The generated solution aims to minimize the rank of the most unsatisfied ones, then the number of most insatisfied on this rank, then the number of more insatisfied on the rank before, and so on. See the PDF for more details on the underlying maths.
