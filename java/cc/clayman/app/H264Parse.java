package cc.clayman.app;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.List;

import cc.clayman.h264.*;


public class H264Parse {
    static String filename = null;
    static boolean showQuality = false;
    
    public static void main(String[] args) {
        if (args.length == 1) {
            filename = args[0];
            
        } else if (args.length == 2) {
            
            if (args[0].equals("-q")) {
                showQuality = true;
            }
            
            filename = args[1];

        } else {
            usage();
        }

        try {
            processFile(filename);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        

    }

    static void usage() {
        System.err.println("H264Parse [-q] filename");
        System.exit(1);
    }

    

    protected static void processFile(String filename) throws IOException {
        TemporalLayerModel model = new TemporalLayerModelGOB16();
        
        H264InputStream str = new H264InputStream(new FileInputStream(filename));

        int count = 0;
        int total = 0;
	// keep track of no of VCLs in:   vclCount
        int vclCount = 0;
        int qualityLayer = 0;
        NAL nal0;
        NAL2 nal;

        boolean prevIsNonVCL  = true;
        
        while ((nal0 = str.getNAL()) != null) {
            // wrap the NAL as a NAL2
            // to get extra functions
            nal = new NAL2(nal0);
                    
            count++;

            total += nal.getSize();


            printNAL(nal, count, total);


            int type = nal.getType();

            if (nal.isVideo()) {

                if (prevIsNonVCL) {
                    vclCount++;
                    prevIsNonVCL = false;
                }

                qualityLayer++;

                long first_mb_in_slice = nal.bs_read_ue(nal);
                long slice_type = nal.bs_read_ue(nal);
                long pic_parameter_set_id = nal.bs_read_ue(nal);
                long frame_num = 0;
                long idr_pic_id = 0;

                        
                if (type >= 1 && type <= 5) {

                    frame_num = nal.bs_read_u(nal, 10);

                    if (type == 5) {
                        idr_pic_id = nal.bs_read_ue(nal);
                    }

    
                    //System.out.printf("SLC HDR: %d %d '%s' %d %d\n", first_mb_in_slice, slice_type, nal.getSliceType((int)slice_type), pic_parameter_set_id, frame_num);
                    //System.out.printf("SLC HDR: %d '%s' %d\n", slice_type, nal.getSliceType((int)slice_type), frame_num);

                    //System.out.printf(" %-20s%10s = %-1d\n", "nal_ref_idc", Integer.toBinaryString(nal.getNRI()), nal.getNRI());       // NRI
                    //System.out.printf(" %-20s%10s = %-1d\n", "nal_unit_type", Integer.toBinaryString(nal.getType()), nal.getType());       // Type
                    //System.out.printf(" %-20s%10s = %-1d\n", "first_mb_in_slice", Integer.toBinaryString((int)first_mb_in_slice), (int)first_mb_in_slice);       // first_mb_in_slice
                    //System.out.printf(" %-20s%10s = %-1d\n", "slice_type", Integer.toBinaryString((int)slice_type), (int)slice_type);       // slice_type
                    //System.out.printf(" %-20s%10s = %-1d\n", "pic_parameter_set_id", Integer.toBinaryString((int)pic_parameter_set_id), (int)pic_parameter_set_id);       // pic_parameter_set_id
                    //System.out.printf(" %-20s%10s = %-1d\n", "frame_num", Integer.toBinaryString((int)frame_num), (int)frame_num);       // frame_num

                    
                }


                // Look at TemporalLayerModelGOB16
                TemporalLayerModel.Tuple currentNAL = model.getLayerInfo(vclCount);

                if (showQuality) {
                    System.out.printf("Frame No: %s Frame: %s Temporal: %s  Qualitylayer: %d\n", vclCount + currentNAL.adjustment, currentNAL.frame, currentNAL.temporal, qualityLayer-1);
                }

            } else {
                prevIsNonVCL = true;
                qualityLayer = 0;
            }
            
 
        }

        str.close();
    }


    /*
    protected static void printNALFull(NAL nal, int count, int total) {
        int size = nal.getSize();

        System.out.printf("NAL: %-6d", count);
        System.out.printf(" bits: %-6d", size*8);
        System.out.printf(" bytes: %-5d", size);
        System.out.printf(" start: %-9d",  (total - size) + 1);  // start bytes
        System.out.printf(" end: %-10d", total + 1);             // end bytes


        System.out.printf(" NRI: %-1d", nal.getNRI());
        System.out.printf(" TYPE: %-2d",nal.getType());
        System.out.printf(" CLASS: %-7s",nal.getTypeClass());
        System.out.printf(" DESC: %s", nal.getTypeString());
        System.out.println();
                    
    }
    */
    
    protected static void printNAL(NAL nal, int count, int total) {
        int size = nal.getSize();

        System.out.printf("%-8d", count);               // N
        System.out.printf(" %-6d", size*8);             // no of bits
        System.out.printf(" %-5d", size);               // no of bytes
        System.out.printf(" %-9d", (total - size) + 1);  // start bytes
        System.out.printf(" %-10d", total);              // end bytes

        System.out.printf(" %-1d", nal.getNRI());       // NRI
        System.out.printf(" %-2d",nal.getType());       // Type
        System.out.printf(" %-7s",nal.getTypeClass());  // VCL or non-VCL
        System.out.printf(" %s", nal.getTypeString());  // Type description
        System.out.println();
                    
    }
    
}
