package com.pokemonoptimizer.pokemon_optimizer_backend.calculator;
import java.util.Arrays;
import java.util.HashMap;
public class CpCalculator {
    public static final double[] CPM_TABLE = new double[103];
    
    static
    {
        // Comes from PvPoke's Pokemon.js cpms array
        CPM_TABLE[2] = 0.0939999967813491;
        CPM_TABLE[3] = 0.135137430784308;
        CPM_TABLE[4] = 0.166397869586944;
        CPM_TABLE[5] = 0.192650914456886;
        CPM_TABLE[6] = 0.215732470154762;
        CPM_TABLE[7] = 0.236572655026622;
        CPM_TABLE[8] = 0.255720049142837;
        CPM_TABLE[9] = 0.273530381100769;
        CPM_TABLE[10] = 0.290249884128570;
        CPM_TABLE[11] = 0.306057381335773;
        CPM_TABLE[12] = 0.321087598800659;
        CPM_TABLE[13] = 0.335445032295077;
        CPM_TABLE[14] = 0.349212676286697;
        CPM_TABLE[15] = 0.362457748778790;
        CPM_TABLE[16] = 0.375235587358474;
        CPM_TABLE[17] = 0.387592411085168;
        CPM_TABLE[18] = 0.399567276239395;
        CPM_TABLE[19] = 0.411193549517250;
        CPM_TABLE[20] = 0.422500014305114;
        CPM_TABLE[21] = 0.432926413410414;
        CPM_TABLE[22] = 0.443107545375824;
        CPM_TABLE[23] = 0.453059953871985;
        CPM_TABLE[24] = 0.462798386812210;
        CPM_TABLE[25] = 0.472336077786704;
        CPM_TABLE[26] = 0.481684952974319;
        CPM_TABLE[27] = 0.490855810259008;
        CPM_TABLE[28] = 0.499858438968658;
        CPM_TABLE[29] = 0.508701756943992;
        CPM_TABLE[30] = 0.517393946647644;
        CPM_TABLE[31] = 0.525942508771329;
        CPM_TABLE[32] = 0.534354329109191;
        CPM_TABLE[33] = 0.542635762230353;
        CPM_TABLE[34] = 0.550792694091796;
        CPM_TABLE[35] = 0.558830599438087;
        CPM_TABLE[36] = 0.566754519939422;
        CPM_TABLE[37] = 0.574569148039264;
        CPM_TABLE[38] = 0.582278907299041;
        CPM_TABLE[39] = 0.589887911977272;
        CPM_TABLE[40] = 0.597400009632110;
        CPM_TABLE[41] = 0.604823657502073;
        CPM_TABLE[42] = 0.612157285213470;
        CPM_TABLE[43] = 0.619404110566050;
        CPM_TABLE[44] = 0.626567125320434;
        CPM_TABLE[45] = 0.633649181622743;
        CPM_TABLE[46] = 0.640652954578399;
        CPM_TABLE[47] = 0.647580963301656;
        CPM_TABLE[48] = 0.654435634613037;
        CPM_TABLE[49] = 0.661219263506722;
        CPM_TABLE[50] = 0.667934000492096;
        CPM_TABLE[51] = 0.674581899290818;
        CPM_TABLE[52] = 0.681164920330047;
        CPM_TABLE[53] = 0.687684905887771;
        CPM_TABLE[54] = 0.694143652915954;
        CPM_TABLE[55] = 0.700542893277978;
        CPM_TABLE[56] = 0.706884205341339;
        CPM_TABLE[57] = 0.713169102333341;
        CPM_TABLE[58] = 0.719399094581604;
        CPM_TABLE[59] = 0.725575616972598;
        CPM_TABLE[60] = 0.731700003147125;
        CPM_TABLE[61] = 0.734741011137376;
        CPM_TABLE[62] = 0.737769484519958;
        CPM_TABLE[63] = 0.740785574597326;
        CPM_TABLE[64] = 0.743789434432983;
        CPM_TABLE[65] = 0.746781208702482;
        CPM_TABLE[66] = 0.749761044979095;
        CPM_TABLE[67] = 0.752729105305821;
        CPM_TABLE[68] = 0.755685508251190;
        CPM_TABLE[69] = 0.758630366519684;
        CPM_TABLE[70] = 0.761563837528228;
        CPM_TABLE[71] = 0.764486065255226;
        CPM_TABLE[72] = 0.767397165298461;
        CPM_TABLE[73] = 0.770297273971590;
        CPM_TABLE[74] = 0.773186504840850;
        CPM_TABLE[75] = 0.776064945942412;
        CPM_TABLE[76] = 0.778932750225067;
        CPM_TABLE[77] = 0.781790064808426;
        CPM_TABLE[78] = 0.784636974334716;
        CPM_TABLE[79] = 0.787473583646825;
        CPM_TABLE[80] = 0.790300011634826;
        CPM_TABLE[81] = 0.792803950958807;
        CPM_TABLE[82] = 0.795300006866455;
        CPM_TABLE[83] = 0.797803921486970;
        CPM_TABLE[84] = 0.800300002098083;
        CPM_TABLE[85] = 0.802803892322847;
        CPM_TABLE[86] = 0.805299997329711;
        CPM_TABLE[87] = 0.807803863460723;
        CPM_TABLE[88] = 0.810299992561340;
        CPM_TABLE[89] = 0.812803834895026;
        CPM_TABLE[90] = 0.815299987792968;
        CPM_TABLE[91] = 0.817803806620319;
        CPM_TABLE[92] = 0.820299983024597;
        CPM_TABLE[93] = 0.822803778631297;
        CPM_TABLE[94] = 0.825299978256225;
        CPM_TABLE[95] = 0.827803750922782;
        CPM_TABLE[96] = 0.830299973487854;
        CPM_TABLE[97] = 0.832803753381377;
        CPM_TABLE[98] = 0.835300028324127;
        CPM_TABLE[99] = 0.837803755931569;
        CPM_TABLE[100] = 0.840300023555755;
        CPM_TABLE[101] = 0.842803729034748;
        CPM_TABLE[102] = 0.845300018787384;
    }

    // https://pokemongohub.net/post/wiki/pokemon-go-calculates-stats-max-cp/
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
    // StatProduct = (BaseAtk + AtkIV) × CPM × (BaseDef + DefIV) × CPM × (BaseSta + StaIV) × CPM
    public static IvResult calculateStatProduct(int baseAtk, int baseDef, int baseSta,
                                             int atkIV, int defIV, int staIV, double level) 
    {
        int attack = baseAtk + atkIV;
        int defense = baseDef + defIV;
        int stamina = baseSta + staIV;
        double index = CPM_TABLE[(int)(level * 2)];
        double effectiveAttack = attack * index;
        double effectiveDefense = defense * index;
        double effectiveStamina = Math.floor(stamina * index);;
        double statProduct = effectiveAttack * effectiveDefense * effectiveStamina;
        return new IvResult(atkIV, defIV, staIV, level, effectiveAttack, statProduct);
    }

    public static RankedIvResult[] rankAllCombos(int baseAtk, int baseDef, int baseSta,
                                 boolean hasCap, int cap, boolean hasBestBuddy)
    { 
        IvResult[] results = new IvResult[4096];
        RankedIvResult[] rankedIvResult = new RankedIvResult[4096];

        for (int atkIV = 0; atkIV < 16; atkIV++)
        {
            for (int defIV = 0; defIV < 16; defIV++)
            {
                for (int staIV = 0; staIV < 16; staIV++)
                {

                    double maxLevel = findMaxLevel(baseAtk, baseDef, baseSta, atkIV, defIV, staIV, hasCap, cap, hasBestBuddy);
                    IvResult ivResult = calculateStatProduct(baseAtk, baseDef, baseSta, atkIV, defIV, staIV, maxLevel);
                    int index = (int)(atkIV * Math.pow(16,2) + defIV * Math.pow(16, 1) + staIV * Math.pow(16, 0));
                    results[index] = ivResult; 
                }
                    
            }
        }

        IvResult[] byStatProduct = results.clone();
        Arrays.sort(byStatProduct, (a,b) -> Double.compare(b.statProduct(), a.statProduct()));
        IvResult[] byEffectiveAttack = results.clone();
        Arrays.sort(byEffectiveAttack, (a,b) -> {
            int p = Double.compare(b.effectiveAttack(), a.effectiveAttack());
            if (p != 0)
            {
                return p;
            }
            return Double.compare(b.statProduct(), a.statProduct());
            });

        HashMap<IvResult, Integer> rankByStatProduct = new HashMap<>();
        HashMap<IvResult, Integer> rankByEffectiveAttack = new HashMap<>();
        for (int i = 0; i < 4096; i++)
        {
            rankByStatProduct.put(byStatProduct[i], i+1);
            rankByEffectiveAttack.put(byEffectiveAttack[i], i + 1);
        }
        for (int i = 0; i < results.length; i++)
        {
            int statProductRank = rankByStatProduct.get(results[i]);
            int mirrorRank = rankByEffectiveAttack.get(results[i]);
            RankedIvResult rankedIv= new RankedIvResult(results[i], statProductRank, mirrorRank);
            rankedIvResult[i] = rankedIv;
        }
        return rankedIvResult;
    }
        
        

    
    public static void main(String[] args)
    {

   //  double level14 = findMaxLevel(152, 194, 200, 14, 15, 15, true, 2500, true);
// System.out.println("atkIV=14 max level: " + level14);

// IvResult result14 = calculateStatProduct(152, 194, 200, 14, 15, 15, level14);
// System.out.println("atkIV=14 stat product: " + result14.statProduct());

// double level13 = findMaxLevel(152, 194, 200, 13, 15, 15, true, 2500, true);
// System.out.println("atkIV=13 max level: " + level13);

// IvResult result13 = calculateStatProduct(152, 194, 200, 13, 15, 15, level13);
// System.out.println("atkIV=13 stat product: " + result13.statProduct());
    
    }
}


