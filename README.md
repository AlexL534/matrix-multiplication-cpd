# Matrix Multiplication Analysis  

**Final Grade:** 17.15/20

This project was developed for the **CPD (Parallel and Distributed Computing)** course in the **LEIC** program. It compares different matrix multiplication approaches, analyzing:  

- **Sequential algorithms**:  
  - Simple O(n³) multiplication  
  - Cache-optimized row multiplication  
  - Block matrix multiplication  

- **Parallel implementations** using OpenMP:  
  - Coarse-grained (outer loop parallelization)  
  - Fine-grained (inner loop parallelization)  

- **Performance metrics**:  
  - Execution time comparisons  
  - Cache efficiency (L1/L2 misses)  
  - Speedup from parallelization  

**Final Grade**: [Your Grade Here]  

---  

## How to Run  

### Prerequisites  
- C++ compiler with OpenMP support  
- Python (optional, for comparison)  

### Run Benchmark  
```bash  
g++ -O2 -fopenmp matrix_mult.cpp -o matrix_mult  
./matrix_mult <size> <algorithm> [block_size] [threads]  
```  

### Algorithms:  
- `simple` - Basic multiplication  
- `row` - Row-optimized  
- `block` - Blocked multiplication  
- `parallel1` - Coarse-grained parallel  
- `parallel2` - Fine-grained parallel  

For full results and analysis, see the pdf report in `assign1/doc`.

Group members:

1. Pedro Borges (up202207552@up.pt)
2. Lucas Faria (up202207540@up.pt)
3. Alexandre Lopes (up202207015@up.pt)
