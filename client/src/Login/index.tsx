/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {useHistory} from 'react-router-dom';
import {Form, Field} from 'react-final-form';

import {login} from 'modules/stores/login';
import {Pages} from 'modules/constants/pages';
import {Container} from './Container';
import {Input} from './Input';
import {FormContainer} from './FormContainer';
import {CopyrightNotice} from './CopyrightNotice';
import {Logo} from './Logo';
import {Title} from './Title';
import {Button} from './Button';
import {LoadingOverlay} from './LoadingOverlay';
import {Error} from './Error';

interface FormValues {
  username: string;
  password: string;
}

const Login: React.FC = () => {
  const [hasError, setHasError] = React.useState(false);
  const history = useHistory();
  const {handleLogin} = login;

  return (
    <Container>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          setHasError(false);
          try {
            await handleLogin(username, password);
            history.push(Pages.Initial);
          } catch {
            setHasError(true);
          }
        }}
      >
        {({handleSubmit, form}) => {
          const {submitting} = form.getState();

          return (
            <form onSubmit={handleSubmit}>
              {submitting && (
                <LoadingOverlay data-testid="login-loading-overlay" />
              )}
              <FormContainer>
                <Logo />
                <Title>Zeebe Tasklist</Title>
                {hasError && <Error>Username and Password do not match.</Error>}
                <Field<FormValues['username']> name="username" type="text">
                  {({input}) => (
                    <Input
                      {...input}
                      placeholder="Username"
                      id={input.name}
                      required
                    />
                  )}
                </Field>
                <Field<FormValues['password']> name="password" type="password">
                  {({input}) => (
                    <Input
                      {...input}
                      placeholder="Password"
                      id={input.name}
                      required
                    />
                  )}
                </Field>
                <Button type="submit" disabled={submitting}>
                  Login
                </Button>
              </FormContainer>
            </form>
          );
        }}
      </Form>
      <CopyrightNotice>
        © Camunda Services GmbH {new Date().getFullYear()}. All rights reserved.
      </CopyrightNotice>
    </Container>
  );
};

export {Login};
