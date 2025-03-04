import time

def OnMult(m_ar : int, m_br : int) -> None:
    temp = 0

    pha = [1.0 for i in range(m_ar) for j in range(m_ar)]
    phb = [i + 1 for i in range(m_br) for j in range(m_br)]
    phc = [0 for i in range(m_ar) for j in range(m_ar)]
    
    time1 = time.time()

    for i in range(0, m_ar):
        for j in range(0, m_br):
            temp = 0
            for k in range(0, m_ar):
                temp += pha[i*m_ar + k] * phb[k*m_br + j]
            phc[i*m_ar + j] = temp
    
    time2 = time.time()

    final = time2 - time1

    print(f'Final Time is {final}')

    for i in range(0, min(10,m_br)):
        print(phc[i])

def OnMultLine(m_ar : int, m_br : int) -> None:
    '''
    Line x line matrix multiplication
    '''

    pha = [1.0 for i in range(m_ar) for j in range(m_ar)]
    phb = [i + 1 for i in range(m_br) for j in range(m_br)]
    phc = [0 for i in range(m_ar) for j in range(m_ar)]
    
    time1 = time.time()

    for i in range(0, m_ar):
        for j in range(0, m_br):
            for k in range(0, m_ar):
                phc[i * m_ar + k] += pha[i*m_ar + j] * phb[j*m_br + k]
    
    time2 = time.time()

    final = time2 - time1

    print(f'Final Time is {final}')

    for i in range(0, min(10,m_br)):
        print(phc[i])
    return None

def OnMultBlock(m_ar : int, m_br : int, bksize : int)  -> None:
    '''
    Block x Block matrix multiplication
    '''

    pha = [1.0 for i in range(m_ar) for j in range(m_ar)]
    phb = [i + 1 for i in range(m_br) for j in range(m_br)]
    phc = [0 for i in range(m_ar) for j in range(m_ar)]
    
    time1 = time.time()

    for i in range(0, m_ar, bksize):
        for j in range(0, m_br, bksize):
            for k in range(0, m_ar, bksize):
                for ii in range(i, min(i + bksize, m_br)):
                    for jj in range(j, min(j + bksize, m_br)):
                        for kk in range(k, min(k + bksize, m_br)):
                            phc[ii * m_ar + kk] += pha[ii*m_ar + jj] * phb[jj*m_br + kk]
    
    time2 = time.time()

    final = time2 - time1

    print(f'Final Time is {final}')

    for i in range(0, min(10,m_br)):
        print(phc[i])
    return None

def main() : 
    while True :
        print("\n1. Multiplication")
        print("2. Line Multiplication")
        print("3. Block Multiplication")
        print("4. Exit\n")

        op = input("Selection?: ")
        try:
            op = int(op)
        except ValueError:
            print("Not a number\n")
            continue

        if op == 4:
            # Exits the program
            return

        dim = input("\nDimensions: lins=cols? ")
        try:
            dim = int(dim)
        except ValueError:
            print("Not a number\n")
            continue

        if op == 1 :
            OnMult(dim,dim)

        elif op == 2 :
            OnMultLine(dim,dim)

        elif op == 3:
            blockSize = input("\nBlock Size? ")
            try:
                blockSize = int(blockSize)
            except ValueError:
                print("Not a number\n")
                continue
            OnMultBlock(dim,dim, blockSize)

        else:
            print("Invalid Option. Try again.\n")

if __name__ == "__main__":
    main()