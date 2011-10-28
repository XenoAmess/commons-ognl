package org.apache.commons.ognl;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.commons.ognl.enhance.ExpressionCompiler;
import org.apache.commons.ognl.enhance.OgnlExpressionCompiler;
import org.apache.commons.ognl.enhance.OrderedReturn;
import org.apache.commons.ognl.enhance.UnsupportedCompilationException;

import java.lang.reflect.Method;

/**
 * $Id$
 *
 * @author Luke Blanshard (blanshlu@netscape.net)
 * @author Drew Davidson (drew@ognl.org)
 */
public class ASTMethod
    extends SimpleNode
    implements OrderedReturn, NodeType
{

    private String methodName;

    private String lastExpression;

    private String coreExpression;

    private Class getterClass;

    public ASTMethod( int id )
    {
        super( id );
    }

    public ASTMethod( OgnlParser p, int id )
    {
        super( p, id );
    }

    /**
     * Called from parser action.
     *
     * @param methodName sets the name of the method
     */
    public void setMethodName( String methodName )
    {
        this.methodName = methodName;
    }

    /**
     * Returns the method name that this node will call.
     *
     * @return the method name
     */
    public String getMethodName()
    {
        return methodName;
    }

    protected Object getValueBody( OgnlContext context, Object source )
        throws OgnlException
    {
        Object[] args = OgnlRuntime.getObjectArrayPool().create( jjtGetNumChildren() );

        try
        {
            Object result, root = context.getRoot();

            for ( int i = 0, icount = args.length; i < icount; ++i )
            {
                args[i] = _children[i].getValue( context, root );
            }

            result = OgnlRuntime.callMethod( context, source, methodName, args );

            if ( result == null )
            {
                NullHandler nh = OgnlRuntime.getNullHandler( OgnlRuntime.getTargetClass( source ) );
                result = nh.nullMethodResult( context, source, methodName, args );
            }

            return result;

        }
        finally
        {
            OgnlRuntime.getObjectArrayPool().recycle( args );
        }
    }

    public String getLastExpression()
    {
        return lastExpression;
    }

    public String getCoreExpression()
    {
        return coreExpression;
    }

    public Class getGetterClass()
    {
        return getterClass;
    }

    public Class getSetterClass()
    {
        return getterClass;
    }

    public String toGetSourceString( OgnlContext context, Object target )
    {
        /*
         * System.out.println("methodName is " + methodName + " for target " + target + " target class: " + (target !=
         * null ? target.getClass() : null) + " current type: " + context.getCurrentType());
         */
        if ( target == null )
        {
            throw new UnsupportedCompilationException( "Target object is null." );
        }

        String post = "";
        String result;
        Method method;

        OgnlExpressionCompiler compiler = OgnlRuntime.getCompiler( context );
        try
        {

            method = OgnlRuntime.getMethod( context, context.getCurrentType() != null
                ? context.getCurrentType()
                : target.getClass(), methodName, _children, false );
            if ( method == null )
            {
                method = OgnlRuntime.getReadMethod( target.getClass(), methodName,
                                                    _children != null ? _children.length : -1 );
            }

            if ( method == null )
            {
                method = OgnlRuntime.getWriteMethod( target.getClass(), methodName,
                                                     _children != null ? _children.length : -1 );

                if ( method != null )
                {

                    context.setCurrentType( method.getReturnType() );
                    context.setCurrentAccessor(
                        compiler.getSuperOrInterfaceClass( method, method.getDeclaringClass() ) );

                    coreExpression = toSetSourceString( context, target );
                    if ( coreExpression == null || coreExpression.length() < 1 )
                    {
                        throw new UnsupportedCompilationException( "can't find suitable getter method" );
                    }

                    coreExpression += ";";
                    lastExpression = "null";

                    return coreExpression;
                }

                return "";
            }
            else
            {

                getterClass = method.getReturnType();
            }

            // TODO: This is a hacky workaround until javassist supports varargs method invocations

            boolean varArgs = OgnlRuntime.isJdk15() && method.isVarArgs();

            if ( varArgs )
            {
                throw new UnsupportedCompilationException(
                    "Javassist does not currently support varargs method calls" );
            }

            result = "." + method.getName() + "(";

            if ( ( _children != null ) && ( _children.length > 0 ) )
            {
                Class[] parms = method.getParameterTypes();
                String prevCast = (String) context.remove( ExpressionCompiler.PRE_CAST );
                /*
                 * System.out.println("before children methodName is " + methodName + " for target " + target +
                 * " target class: " + (target != null ? target.getClass() : null) + " current type: " +
                 * context.getCurrentType() + " and previous type: " + context.getPreviousType());
                 */

                for ( int i = 0; i < _children.length; i++ )
                {
                    if ( i > 0 )
                    {
                        result = result + ", ";
                    }

                    Class prevType = context.getCurrentType();

                    Object root = context.getRoot();
                    context.setCurrentObject( root );
                    context.setCurrentType( root != null ? root.getClass() : null );
                    context.setCurrentAccessor( null );
                    context.setPreviousType( null );

                    Node child = _children[i];

                    String parmString = ASTMethodUtil.getParmString( context, root, child, prevType );

                    Class valueClass = ASTMethodUtil.getValueClass( context, root, child );

                    if ( ( !varArgs || varArgs && ( i + 1 ) < parms.length ) && valueClass != parms[i] )
                    {
                        parmString = ASTMethodUtil.getParmString( context, parms[i], parmString, child, valueClass,
                                                                  ".class, true)" );
                    }

                    result += parmString;
                }

                if ( prevCast != null )
                {
                    context.put( ExpressionCompiler.PRE_CAST, prevCast );
                }
            }

        }
        catch ( Throwable t )
        {
            throw OgnlOps.castToRuntime( t );
        }

        try
        {
            Object contextObj = getValueBody( context, target );
            context.setCurrentObject( contextObj );
        }
        catch ( Throwable t )
        {
            throw OgnlOps.castToRuntime( t );
        }

        result += ")" + post;

        if ( method.getReturnType() == void.class )
        {
            coreExpression = result + ";";
            lastExpression = "null";
        }

        context.setCurrentType( method.getReturnType() );
        context.setCurrentAccessor( compiler.getSuperOrInterfaceClass( method, method.getDeclaringClass() ) );

        return result;
    }

    public String toSetSourceString( OgnlContext context, Object target )
    {
        /*
         * System.out.println("current type: " + context.getCurrentType() + " target:" + target + " " +
         * context.getCurrentObject() + " last child? " + lastChild(context));
         */
        Method m =
            OgnlRuntime.getWriteMethod( context.getCurrentType() != null ? context.getCurrentType() : target.getClass(),
                                        methodName, _children != null ? _children.length : -1 );
        if ( m == null )
        {
            throw new UnsupportedCompilationException(
                "Unable to determine setter method generation for " + methodName );
        }

        String post = "";
        String result = "." + m.getName() + "(";

        if ( m.getReturnType() != void.class && m.getReturnType().isPrimitive() && ( _parent == null
            || !ASTTest.class.isInstance( _parent ) ) )
        {
            Class wrapper = OgnlRuntime.getPrimitiveWrapperClass( m.getReturnType() );

            ExpressionCompiler.addCastString( context, "new " + wrapper.getName() + "(" );
            post = ")";
            getterClass = wrapper;
        }

        boolean varArgs = OgnlRuntime.isJdk15() && m.isVarArgs();

        if ( varArgs )
        {
            throw new UnsupportedCompilationException( "Javassist does not currently support varargs method calls" );
        }

        OgnlExpressionCompiler compiler = OgnlRuntime.getCompiler( context );
        try
        {
            /*
             * if (lastChild(context) && m.getParameterTypes().length > 0 && _children.length <= 0) throw new
             * UnsupportedCompilationException("Unable to determine setter method generation for " + m);
             */

            if ( ( _children != null ) && ( _children.length > 0 ) )
            {
                Class[] parms = m.getParameterTypes();
                String prevCast = (String) context.remove( ExpressionCompiler.PRE_CAST );

                for ( int i = 0; i < _children.length; i++ )
                {
                    if ( i > 0 )
                    {
                        result += ", ";
                    }

                    Class prevType = context.getCurrentType();

                    context.setCurrentObject( context.getRoot() );
                    context.setCurrentType( context.getRoot() != null ? context.getRoot().getClass() : null );
                    context.setCurrentAccessor( null );
                    context.setPreviousType( null );

                    Node child = _children[i];
                    Object value = child.getValue( context, context.getRoot() );
                    String parmString = child.toSetSourceString( context, context.getRoot() );

                    if ( context.getCurrentType() == Void.TYPE || context.getCurrentType() == void.class )
                    {
                        throw new UnsupportedCompilationException( "Method argument can't be a void type." );
                    }

                    if ( parmString == null || parmString.trim().length() < 1 )
                    {
                        if ( ASTProperty.class.isInstance( child ) || ASTMethod.class.isInstance( child )
                            || ASTStaticMethod.class.isInstance( child ) || ASTChain.class.isInstance( child ) )
                        {
                            throw new UnsupportedCompilationException(
                                "ASTMethod setter child returned null from a sub property expression." );
                        }
                        parmString = "null";
                    }

                    // to undo type setting of constants when used as method parameters
                    if ( ASTConst.class.isInstance( child ) )
                    {
                        context.setCurrentType( prevType );
                    }

                    parmString = ExpressionCompiler.getRootExpression( child, context.getRoot(), context ) + parmString;

                    String cast = "";
                    if ( ExpressionCompiler.shouldCast( child ) )
                    {
                        cast = (String) context.remove( ExpressionCompiler.PRE_CAST );
                    }

                    if ( cast == null )
                    {
                        cast = "";
                    }

                    parmString = cast + parmString;

                    Class valueClass = value != null ? value.getClass() : null;
                    if ( NodeType.class.isAssignableFrom( child.getClass() ) )
                    {
                        valueClass = ( (NodeType) child ).getGetterClass();
                    }

                    if ( valueClass != parms[i] )
                    {
                        parmString =
                            ASTMethodUtil.getParmString( context, parms[i], parmString, child, valueClass, ".class)" );
                    }

                    result += parmString;
                }

                if ( prevCast != null )
                {
                    context.put( ExpressionCompiler.PRE_CAST, prevCast );
                }
            }

        }
        catch ( Throwable t )
        {
            throw OgnlOps.castToRuntime( t );
        }

        try
        {
            Object contextObj = getValueBody( context, target );
            context.setCurrentObject( contextObj );
        }
        catch ( Throwable t )
        {
            // ignore
        }

        context.setCurrentType( m.getReturnType() );
        context.setCurrentAccessor( compiler.getSuperOrInterfaceClass( m, m.getDeclaringClass() ) );

        return result + ")" + post;
    }

    public <R, P> R accept( NodeVisitor<? extends R, ? super P> visitor, P data )
        throws OgnlException
    {
        return visitor.visit( this, data );
    }
}
