public class CyclicBarrierDEMO {
  
    public static void main(String[] args) {
        //4名跑步运队员
        int num = 4;
        CyclicBarrier barrier  = new CyclicBarrier(num);
      	
      	//都开始去往起点(带子)
        for(int i = 0; i < N;i++)
            new Runner(barrier).start();
        }
	
		//
        static class Runner extends Thread{
            
            private CyclicBarrier cyclicBarrier;
          
            public Writer(CyclicBarrier cyclicBarrier) {
                this.cyclicBarrier = cyclicBarrier;
            }

            @Override
            public void run() {
                System.out.println("运动员"+Thread.currentThread().getName()+"准备去起点带...");
                try {
                    //模拟去起点
                    Thread.sleep(1000);      
                    System.out.println("运动员"+Thread.currentThread().getName()+"到达起点，等待其他线运动员到达起点");
                    cyclicBarrier.await();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("所有队员到达起点，同时开始跑步...");
            }
        }
	｝
}

运动员Thread-0准备去起点带...
运动员Thread-3准备去起点带...
运动员Thread-2准备去起点带...
运动员Thread-1准备去起点带...
运动员Thread-2到达起点，等待其他线运动员到达起点
运动员Thread-0到达起点，等待其他线运动员到达起点
运动员Thread-3到达起点，等待其他线运动员到达起点
运动员Thread-1到达起点，等待其他线运动员到达起点
所有队员到达起点，同时开始跑步...
所有队员到达起点，同时开始跑步...
所有队员到达起点，同时开始跑步...
所有队员到达起点，同时开始跑步...
