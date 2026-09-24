with open ("table.txt") as f:
    for line in f:
        value = line.split()
        level = float(value[0])
        cpm = float(value[1])
        arr_index = int(level * 2)
        print(f"        CPM_TABLE[{arr_index}] = {cpm};")

