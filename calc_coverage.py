import sys

in_file = sys.argv[1]

all_stmts = dict()
with open(in_file, 'r') as f:
    for line in f:
        method, stmt = line.split(", ")[1:3]
        bytecode_stmt = method  + stmt
        if bytecode_stmt not in all_stmts:
            all_stmts[bytecode_stmt] =0
        all_stmts[bytecode_stmt] = all_stmts[bytecode_stmt] + 1

print("Exec stmts")
print(len(all_stmts))

print("Exec stmt instances")
print(sum(all_stmts.values()))


