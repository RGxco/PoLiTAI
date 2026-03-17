import os
import sys

def chunk_file(input_file, output_prefix, chunk_size=1024 * 1024 * 410): # ~410MB chunks
    if not os.path.exists(input_file):
        print(f"Error: {input_file} not found.")
        sys.exit(1)
        
    file_size = os.path.getsize(input_file)
    print(f"File size: {file_size / (1024*1024):.2f} MB")
    
    with open(input_file, 'rb') as f:
        chunk_num = 1
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
                
            output_file = f"{output_prefix}.part{chunk_num}"
            print(f"Writing {output_file}...")
            target_path = os.path.join(os.path.dirname(input_file), os.path.basename(output_file))
            with open(target_path, 'wb') as out_f:
                out_f.write(chunk)
            chunk_num += 1
            
    print("Slicing complete!")

if __name__ == "__main__":
    # Target the file already in assets
    found_path = r"C:\Users\imnot\AndroidStudioProjects\PoLitAI_Phi3\app\src\main\assets\gemma-3n-E4B-it-int4.task"
    
    output_prefix = "gemma_3n_4b"
    chunk_file(found_path, output_prefix)
    
    # DELETE the original to prevent build crash
    os.remove(found_path)
    print(f"Deleted original massive file: {found_path}")
