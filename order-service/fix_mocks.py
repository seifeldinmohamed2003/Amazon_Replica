import os
import re

directory = 'src/test/java/com/team27/amazon/order/service/'
fields_to_mock = [
    'OrderRepository',
    'ShipmentJdbcRepository',
    'ShippingAddressJdbcRepository',
    'ProductJdbcRepository',
    'TransactionJdbcRepository',
    'ProductServiceClient',
    'UserServiceClient',
    'OrderEventPublisher'
]

modified_files = []

for filename in os.listdir(directory):
    if filename.startswith('OrderService') and filename.endswith('Test.java'):
        filepath = os.path.join(directory, filename)
        with open(filepath, 'r') as f:
            content = f.read()

        original_content = content
        
        # Regex to find private [FieldType] [fieldName]; that doesn't have @Mock above it
        # We look for lines NOT preceded by @Mock (or any @ annotation for simplicity here, but specific is better)
        for field in fields_to_mock:
            # Pattern: (Not @Mock) + (\n) + (private fieldType fieldName;)
            # Using negative lookbehind is tricky with multiline. 
            # Simplified approach: find all occurrences of the field declaration and replace if @Mock is missing.
            
            pattern = rf'(?<!@Mock\n)(\s+)(private\s+{field}\s+\w+;)'
            # Use a more robust one that handles existing annotations and whitespace
            # This looks for the declaration and checks if @Mock is in the previous line
            
            lines = content.splitlines()
            new_lines = []
            changed = False
            for i in range(len(lines)):
                line = lines[i]
                found_field = False
                for f_type in fields_to_mock:
                    if re.search(rf'private\s+{f_type}\s+\w+;', line):
                        # Check if previous line (ignoring empty lines) is @Mock
                        prev_idx = i - 1
                        is_mocked = False
                        while prev_idx >= 0:
                            prev_line = lines[prev_idx].strip()
                            if prev_line == '@Mock':
                                is_mocked = True
                                break
                            if prev_line == '' or prev_line.startswith('//'):
                                prev_idx -= 1
                                continue
                            break
                        
                        if not is_mocked:
                            indent = re.match(r'^\s*', line).group()
                            new_lines.append(f'{indent}@Mock')
                            changed = True
                        break
                new_lines.append(line)
            content = '\n'.join(new_lines) + '\n'

        if content != original_content:
            with open(filepath, 'w') as f:
                f.write(content)
            modified_files.append(filename)

print("Modified files:")
for f in modified_files:
    print(f)
