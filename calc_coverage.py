import sys
import json

trace_file = sys.argv[1]
static_log_file = sys.argv[2]

all_bytecode_stmts = dict()
all_java_stmts = dict()
old_java_stmt = ""
with open(trace_file, 'r') as f:
    for line in f:
        method, clazz, stmt = line.split(", ")[1:4]
        method = method.replace(";", ",")
        print(stmt)
        if ":LINENO:" in stmt:
            stmt, line_no = stmt.split(":LINENO:")[0:2]
            line_no = line_no.split(":")[0]
        else:
            stmt = stmt.split(":FILE:")[0]
            line_no = None
        
        if ":FIELD:" in stmt:
            stmt = stmt.split(":FIELD:")[0]

        bytecode_stmt = "<" + clazz + ": " + method + ">" + ":" + stmt

        if line_no:
            java_stmt = "<" + clazz + ": " + method + ">" + ":" + line_no
        else:
            java_stmt = None

        # print(java_stmt)
        # print(bytecode_stmt)
        if bytecode_stmt not in all_bytecode_stmts:
            all_bytecode_stmts[bytecode_stmt] = 0
        all_bytecode_stmts[bytecode_stmt] = all_bytecode_stmts[bytecode_stmt] + 1

        if java_stmt:
            if java_stmt not in all_java_stmts:
                all_java_stmts[java_stmt] = 0
            if old_java_stmt != java_stmt:
                all_java_stmts[java_stmt] = all_java_stmts[java_stmt] + 1

        old_java_stmt = java_stmt

print("Exec bytecode stmts")
print(len(all_bytecode_stmts))

print("Exec bytecode stmt instances")
print(sum(all_bytecode_stmts.values()))

print("Exec java stmts")
print(len(all_java_stmts))

print("Exec java stmt instances")
print(sum(all_java_stmts.values()))


with open(static_log_file, 'r') as f:
    data = json.load(f)

# print(data)

all_possible_bytecode_stmts = set()
all_possible_java_stmts = set()

for m in data:
    # print("-------------")
    # print(m)
    for l in data[m]:
        java_stmt = m + ":" + l
        all_possible_java_stmts.add(java_stmt)
        for b in data[m][l]:
            bytecode_stmt = m + ":" + b
            all_possible_bytecode_stmts.add(bytecode_stmt)

print("Bytecode stmts coverage")
print(100*len(all_bytecode_stmts)/len(all_possible_bytecode_stmts))

print("Java stmts coverage")
print(100*len(all_java_stmts)/len(all_possible_java_stmts))
