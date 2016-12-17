package cn.wjdiankong.main;

import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import cn.wjdiankong.patch.ChangeQuickRedirect;
import cn.wjdiankong.robust.utils.PatchProxy;

public final class RobustAsmUtils {
	
	public final static String REDIRECTFIELD_NAME = "changeQuickRedirect";
	public final static String REDIRECTCLASSNAME = Type.getDescriptor(ChangeQuickRedirect.class);
	public final static String PROXYCLASSNAME = PatchProxy.class.getName().replace(".", "/");
	
	public static void addClassStaticField(ClassVisitor cv, String fieldName, Class<?> typeClass){
		cv.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, fieldName,
				Type.getDescriptor(typeClass), null, null); 
	}
	
	public static void addClassStaticField(ClassVisitor cv, String fieldName, String typeClass){
		cv.visitField(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, fieldName, typeClass, null, null); 
	}
	
	public static void setClassStaticFieldValue(MethodVisitor mv, String className, String fieldName, String typeSign){
		mv.visitFieldInsn(Opcodes.PUTSTATIC, className, fieldName, typeSign);
	}
	
	public static void getClassStaticFieldValue(MethodVisitor mv, String className, String fieldName, String typeSign){
		mv.visitFieldInsn(Opcodes.GETSTATIC, className, fieldName, typeSign);
	}
	
	/**
	 * �������
	 * @param mv
	 * @param className
	 * @param paramsTypeClass
	 * @param returnTypeStr
	 * @param isStatic
	 */
	public static void createInsertCode(MethodVisitor mv, String className, List<String> paramsTypeClass, String returnTypeStr, boolean isStatic){
		//��ȡchangeQuickRedirect��̬����
		mv.visitFieldInsn(Opcodes.GETSTATIC, 
				className, 
				REDIRECTFIELD_NAME, 
				REDIRECTCLASSNAME);
		Label l1 = new Label();
		mv.visitJumpInsn(Opcodes.IFNULL, l1);

		/**
		 * ����isSupport����
		 */
		//��һ��������new Object[]{...};,�������û�в���ֱ�Ӵ���new Object[0]
		if(paramsTypeClass.size() == 0){
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		}else{
			createObjectArray(mv, paramsTypeClass, isStatic);
		}
		
		//�ڶ���������this,���������static�Ļ���ֱ�Ӵ���null
		if(isStatic){
			mv.visitInsn(Opcodes.ACONST_NULL);
		}else{
			mv.visitVarInsn(Opcodes.ALOAD, 0);
		}
		
		//������������changeQuickRedirect
		mv.visitFieldInsn(Opcodes.GETSTATIC, 
				className, 
				REDIRECTFIELD_NAME, 
				REDIRECTCLASSNAME);
		
		//���ĸ�������false,��־�Ƿ�Ϊstatic
		mv.visitInsn(isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		
		//��ʼ����
		mv.visitMethodInsn(Opcodes.GETSTATIC, 
				PROXYCLASSNAME, 
				"isSupport", 
				"([Ljava/lang/Object;Ljava/lang/Object;"+REDIRECTCLASSNAME+"Z)Z");
		mv.visitJumpInsn(Opcodes.IFEQ, l1);
		
		/**
		 * ����accessDispatch����
		 */
		//��һ��������new Object[]{...};,�������û�в���ֱ�Ӵ���new Object[0]
		if(paramsTypeClass.size() == 0){
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		}else{
			createObjectArray(mv, paramsTypeClass, isStatic);
		}		
		
		//�ڶ���������this,���������static�Ļ���ֱ�Ӵ���null
		if(isStatic){
			mv.visitInsn(Opcodes.ACONST_NULL);
		}else{
			mv.visitVarInsn(Opcodes.ALOAD, 0);
		}		
		
		//����������:changeQuickRedirect
		mv.visitFieldInsn(Opcodes.GETSTATIC,
				className, 
				REDIRECTFIELD_NAME, 
				REDIRECTCLASSNAME);
		//���ĸ�������false,��־�Ƿ�Ϊstatic
		mv.visitInsn(isStatic ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		
		//��ʼ����
		mv.visitMethodInsn(Opcodes.INVOKESTATIC,
				PROXYCLASSNAME, 
				"accessDispatch", 
				"([Ljava/lang/Object;Ljava/lang/Object;"+REDIRECTCLASSNAME+"Z)Ljava/lang/Object;");
		
		//�ж��Ƿ��з���ֵ�����벻ͬ
		if("V".equals(returnTypeStr)){
			mv.visitInsn(Opcodes.POP);
			mv.visitInsn(Opcodes.RETURN);
		}else{
			//ǿ��ת������
			if(!castPrimateToObj(mv, returnTypeStr)){
				//������Ҫע�⣬������������͵�ֱ��ʹ�ü��ɣ�������������ͣ��͵�ȥ��ǰ׺��,����������û�н�����;
				//���磺Ljava/lang/String; ==�� java/lang/String
				String newTypeStr = null;
				int len = returnTypeStr.length();
				if(returnTypeStr.startsWith("[")){
					newTypeStr = returnTypeStr.substring(0, len);
				}else{
					newTypeStr = returnTypeStr.substring(1, len-1);
				}
				mv.visitTypeInsn(Opcodes.CHECKCAST, newTypeStr);
			}
			
			//���ﻹ��Ҫ���������Ͳ�ͬ����ָ��Ҳ��ͬ
			mv.visitInsn(getReturnTypeCode(returnTypeStr));
		}
		
		mv.visitLabel(l1);
	}
	
	/**
	 * �����ֲ���������
	 * @param mv
	 * @param paramsTypeClass
	 * @param isStatic
	 */
	private static void createObjectArray(MethodVisitor mv, List<String> paramsTypeClass, boolean isStatic){
		//Opcodes.ICONST_0 ~ Opcodes.ICONST_5 ���ָ�Χ
		int argsCount = paramsTypeClass.size();
		//���� Object[argsCount];
		if(argsCount >= 6){
			mv.visitIntInsn(Opcodes.BIPUSH, argsCount);
		}else{
			mv.visitInsn(Opcodes.ICONST_0+argsCount);
		}
		mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
		
		//�����static������û��this��������
		int loadIndex = (isStatic ? 0 : 1);
		
		//�����������
		for(int i=0;i<argsCount;i++){
			mv.visitInsn(Opcodes.DUP);
			if(i <= 5){
				mv.visitInsn(Opcodes.ICONST_0+i);
			}else{
				mv.visitIntInsn(Opcodes.BIPUSH, i);
			}
			
			//������Ҫ�����⴦����ʵ�������з��ָ����⣺public void xxx(long a, boolean b, double c,int d)
			//��һ��������ǰ��һ��������long������double���͵Ļ������������ʹ��LOADָ�������������ֵҪ+1
			//���˲����Ǻ�long��double��8���ֽڵ������й�ϵ���������˴���
			//��������Ĳ�����[a=LLOAD 1] [b=ILOAD 3] [c=DLOAD 4] [d=ILOAD 6];
			if(i >= 1){
				//������Ҫ�жϵ�ǰ������ǰ��һ��������������ʲô
				if("J".equals(paramsTypeClass.get(i-1)) || "D".equals(paramsTypeClass.get(i-1))){
					//���ǰ��һ��������long��double���ͣ�loadָ��������Ҫ����1
					loadIndex ++;
				}
			}
			if(!createPrimateTypeObj(mv, loadIndex, paramsTypeClass.get(i))){
				mv.visitVarInsn(Opcodes.ALOAD, loadIndex);
				mv.visitInsn(Opcodes.AASTORE);
			}
			loadIndex ++;
		}
	}
	
	private static void createBooleanObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Byte");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ILOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Byte", "<init>", "(B)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createShortObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Short");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ILOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Short", "<init>", "(S)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createCharObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Character");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ILOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Character", "<init>", "(C)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createIntegerObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Integer");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.ILOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Integer", "<init>", "(I)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createFloatObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Float");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.FLOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Float", "<init>", "(F)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createDoubleObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Double");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.DLOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Double", "<init>", "(D)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	private static void createLongObj(MethodVisitor mv, int argsPostion){
		mv.visitTypeInsn(Opcodes.NEW, "java/lang/Long");
		mv.visitInsn(Opcodes.DUP);
		mv.visitVarInsn(Opcodes.LLOAD, argsPostion);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Long", "<init>", "(J)V");
		mv.visitInsn(Opcodes.AASTORE);
	}
	
	/**
	 * �����������Ͷ�Ӧ�Ķ���
	 * @param mv
	 * @param argsPostion
	 * @param typeS
	 * @return
	 */
	private static boolean createPrimateTypeObj(MethodVisitor mv, int argsPostion, String typeS){
		if("Z".equals(typeS)){
			createBooleanObj(mv, argsPostion);
			return true;
		}
		if("B".equals(typeS)){
			createBooleanObj(mv, argsPostion);
			return true;
		}
		if("C".equals(typeS)){
			createCharObj(mv, argsPostion);
			return true;
		}
		if("S".equals(typeS)){
			createShortObj(mv, argsPostion);
			return true;
		}
		if("I".equals(typeS)){
			createIntegerObj(mv, argsPostion);
			return true;
		}
		if("F".equals(typeS)){
			createFloatObj(mv, argsPostion);
			return true;
		}
		if("D".equals(typeS)){
			createDoubleObj(mv, argsPostion);
			return true;
		}
		if("J".equals(typeS)){
			createLongObj(mv, argsPostion);
			return true;
		}
		return false;
	}
	
	/**
	 * ����������Ҫ���������ͷ�װ
	 * @param mv
	 * @param typeS
	 * @return
	 */
	private static boolean castPrimateToObj(MethodVisitor mv, String typeS){
		if("Z".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z");
			return true;
		}
		if("B".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B");
			return true;
		}
		if("C".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "intValue", "()C");
			return true;
		}
		if("S".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S");
			return true;
		}
		if("I".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I");
			return true;
		}
		if("F".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F");
			return true;
		}
		if("D".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D");
			return true;
		}
		if("J".equals(typeS)){
			mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");//ǿ��ת������
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J");
			return true;
		}
		return false;
	}
	
	/**
	 * ��Բ�ͬ���ͷ���ָ�һ��
	 * @param typeS
	 * @return
	 */
	private static int getReturnTypeCode(String typeS){
		if("Z".equals(typeS)){
			return Opcodes.IRETURN;
		}
		if("B".equals(typeS)){
			return Opcodes.IRETURN;
		}
		if("C".equals(typeS)){
			return Opcodes.IRETURN;
		}
		if("S".equals(typeS)){
			return Opcodes.IRETURN;
		}
		if("I".equals(typeS)){
			return Opcodes.IRETURN;
		}
		if("F".equals(typeS)){
			return Opcodes.FRETURN;
		}
		if("D".equals(typeS)){
			return Opcodes.DRETURN;
		}
		if("J".equals(typeS)){
			return Opcodes.LRETURN;
		}
		return Opcodes.ARETURN;
	}
	
}
