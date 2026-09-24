public class CpCalculator {
    public static final double[] CPM_TABLE = new double[103];
    
    static
    {
        CPM_TABLE[2] = 0.094;
        CPM_TABLE[3] = 0.1351374318;
        CPM_TABLE[4] = 0.16639787;
        CPM_TABLE[5] = 0.192650919;
        CPM_TABLE[6] = 0.21573247;
        CPM_TABLE[7] = 0.2365726613;
        CPM_TABLE[8] = 0.25572005;
        CPM_TABLE[9] = 0.2735303812;
        CPM_TABLE[10] = 0.29024988;
        CPM_TABLE[11] = 0.3060573775;
        CPM_TABLE[12] = 0.3210876;
        CPM_TABLE[13] = 0.3354450362;
        CPM_TABLE[14] = 0.34921268;
        CPM_TABLE[15] = 0.3624577511;
        CPM_TABLE[16] = 0.3752356;
        CPM_TABLE[17] = 0.387592416;
        CPM_TABLE[18] = 0.39956728;
        CPM_TABLE[19] = 0.4111935514;
        CPM_TABLE[20] = 0.4225;
        CPM_TABLE[21] = 0.4329264091;
        CPM_TABLE[22] = 0.44310755;
        CPM_TABLE[23] = 0.4530599591;
        CPM_TABLE[24] = 0.4627984;
        CPM_TABLE[25] = 0.472336093;
        CPM_TABLE[26] = 0.48168495;
        CPM_TABLE[27] = 0.4908558003;
        CPM_TABLE[28] = 0.49985844;
        CPM_TABLE[29] = 0.508701765;
        CPM_TABLE[30] = 0.51739395;
        CPM_TABLE[31] = 0.5259425113;
        CPM_TABLE[32] = 0.5343543;
        CPM_TABLE[33] = 0.5426357375;
        CPM_TABLE[34] = 0.5507927;
        CPM_TABLE[35] = 0.5588305862;
        CPM_TABLE[36] = 0.5667545;
        CPM_TABLE[37] = 0.5745691333;
        CPM_TABLE[38] = 0.5822789;
        CPM_TABLE[39] = 0.5898879072;
        CPM_TABLE[40] = 0.5974;
        CPM_TABLE[41] = 0.6048236651;
        CPM_TABLE[42] = 0.6121573;
        CPM_TABLE[43] = 0.6194041216;
        CPM_TABLE[44] = 0.6265671;
        CPM_TABLE[45] = 0.6336491432;
        CPM_TABLE[46] = 0.64065295;
        CPM_TABLE[47] = 0.6475809666;
        CPM_TABLE[48] = 0.65443563;
        CPM_TABLE[49] = 0.6612192524;
        CPM_TABLE[50] = 0.667934;
        CPM_TABLE[51] = 0.6745818959;
        CPM_TABLE[52] = 0.6811649;
        CPM_TABLE[53] = 0.6876849038;
        CPM_TABLE[54] = 0.69414365;
        CPM_TABLE[55] = 0.70054287;
        CPM_TABLE[56] = 0.7068842;
        CPM_TABLE[57] = 0.7131691091;
        CPM_TABLE[58] = 0.7193991;
        CPM_TABLE[59] = 0.7255756136;
        CPM_TABLE[60] = 0.7317;
        CPM_TABLE[61] = 0.7347410093;
        CPM_TABLE[62] = 0.7377695;
        CPM_TABLE[63] = 0.7407855938;
        CPM_TABLE[64] = 0.74378943;
        CPM_TABLE[65] = 0.7467812109;
        CPM_TABLE[66] = 0.74976104;
        CPM_TABLE[67] = 0.7527290867;
        CPM_TABLE[68] = 0.7556855;
        CPM_TABLE[69] = 0.7586303683;
        CPM_TABLE[70] = 0.76156384;
        CPM_TABLE[71] = 0.7644860647;
        CPM_TABLE[72] = 0.76739717;
        CPM_TABLE[73] = 0.7702972656;
        CPM_TABLE[74] = 0.7731865;
        CPM_TABLE[75] = 0.7760649616;
        CPM_TABLE[76] = 0.77893275;
        CPM_TABLE[77] = 0.7817900548;
        CPM_TABLE[78] = 0.784637;
        CPM_TABLE[79] = 0.7874736075;
        CPM_TABLE[80] = 0.7903;
        CPM_TABLE[81] = 0.792803968;
        CPM_TABLE[82] = 0.79530001;
        CPM_TABLE[83] = 0.797800015;
        CPM_TABLE[84] = 0.8003;
        CPM_TABLE[85] = 0.802799995;
        CPM_TABLE[86] = 0.8053;
        CPM_TABLE[87] = 0.8078;
        CPM_TABLE[88] = 0.81029999;
        CPM_TABLE[89] = 0.812799985;
        CPM_TABLE[90] = 0.81529999;
        CPM_TABLE[91] = 0.81779999;
        CPM_TABLE[92] = 0.82029999;
        CPM_TABLE[93] = 0.82279999;
        CPM_TABLE[94] = 0.82529999;
        CPM_TABLE[95] = 0.82779999;
        CPM_TABLE[96] = 0.83029999;
        CPM_TABLE[97] = 0.83279999;
        CPM_TABLE[98] = 0.83529999;
        CPM_TABLE[99] = 0.83779999;
        CPM_TABLE[100] = 0.84029999;
        CPM_TABLE[101] = 0.84279999;
        CPM_TABLE[102] = 0.84529999;
    }

    public static int calculateCP(int baseAtk, int baseDef, int baseSta,
                                 int atkIV, int defIV, int staIV, double level) {
    //formula here
        int attack = baseAtk + atkIV;
        int defense = baseDef + defIV;
        int stamina = baseSta + staIV;
        double max_cp = Math.floor(
            Math.max(10, 
                (attack * 
                    Math.pow(defense, 0.5) * 
                    Math.pow(stamina, 0.5) * 
                    Math.pow(CPM_TABLE[(int)(level*2)], 2))/10));
        return (int)max_cp;
        
    }

    public static double findMaxLevel(int baseAtk, int baseDef, int baseSta,
                                 int atkIV, int defIV, int staIV, 
                                 boolean hasCap, int cap, boolean hasBestBuddy)
    {
        int maxLevelIndex = hasBestBuddy ? 102 : 100;
        if (!hasCap)
        {
            return maxLevelIndex / 2.0;
        }
        else
        {
            int high = maxLevelIndex;
            int low = 2;
            int mid = (low + high)/2;
            int computedCP = calculateCP(baseAtk, baseDef, baseSta, atkIV, defIV, staIV, maxLevelIndex/2.0);
            double bestValidIndex = low;

            while (low <= high)
            {
                computedCP = calculateCP(baseAtk, baseDef, baseSta, atkIV, defIV, staIV, mid/2.0);
                if (computedCP <= cap)
                {
                    bestValidIndex = mid;
                    low = mid + 1;
                } 
                else
                {
                    high = mid - 1;
                }
                mid = (low + high)/2;
            }
            return bestValidIndex/2.0;
        }
    }
    public static void main(String[] args)
    {

    // Sweep atk IV 0-15 holding def/sta at the rank 1 values, to see the level curve
    for (int atk = 0; atk <= 15; atk++) {
    double level = findMaxLevel(226, 190, 214, atk, 15, 14, true, 1500, false);
    System.out.println("ATK IV " + atk + "/DEF 15/STA 14 -> max level " + level);
    }
    }
}


