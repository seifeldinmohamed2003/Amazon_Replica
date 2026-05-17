import os
import re

files = [f for f in os.listdir('.') if f.startswith('OrderService') and f.endswith('Test.java') and 'S3F12' not in f]

modified_files = []

for filename in files:
    with open(filename, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if "@InjectMocks\n    private OrderService" in content and "ProductServiceClient" not in content:
        # Add imports
        import_product = "import com.team27.amazon.contracts.feign.ProductServiceClient;"
        import_user = "import com.team27.amazon.contracts.feign.UserServiceClient;"
        
        if import_product not in content:
            content = re.sub(r'(package .*?;)', r'\1\n\n' + import_product, content)
        if import_user not in content:
            content = re.sub(r'(import .*?;)', r'\1\n' + import_user, content, count=1)
            
        # Add mocks
        mocks = "    @Mock\n    private ProductServiceClient productServiceClient;\n    @Mock\n    private UserServiceClient userServiceClient;\n\n"
        content = content.replace("    @InjectMocks", mocks + "    @InjectMocks")
        
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        modified_files.append(filename)

print(f"Modified files: {', '.join(modified_files) if modified_files else 'None'}")
